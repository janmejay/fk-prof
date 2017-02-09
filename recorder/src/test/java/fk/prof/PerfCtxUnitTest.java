package fk.prof;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class PerfCtxUnitTest {
    private TestJni testJni;

    @Before
    public void setUp() {
        TestJni.loadJniLib();
        testJni = new TestJni();
        testJni.setupPerfCtx();
        testJni.setupThdTracker();
    }
    
    @After
    public void tearDown() {
        testJni.teardownThdTracker();
        testJni.teardownPerfCtx();
    }

    @Test
    public void shouldUnderstandEquality() {
        PerfCtx foo = new PerfCtx("foo", 10);
        PerfCtx bar = new PerfCtx("bar", 15);
        assertThat(foo, not(bar));
        PerfCtx baz = new PerfCtx("foo", 10);
        assertThat(foo, is(baz));
    }

    @Test
    public void shouldTrackCtx() {
        PerfCtx foo = new PerfCtx("foo", 15);
        long[] ctxs = new long[5];
        assertThat(testJni.getCurrentCtx(ctxs), is(0));
        foo.begin();
        assertThat(testJni.getCurrentCtx(ctxs), is(1));
        assertCtxIs(ctxs[0], "foo", 15, MergeSemantics.MERGE_TO_PARENT, false);
        foo.end();
        assertThat(testJni.getCurrentCtx(ctxs), is(0));
    }

    private void assertCtxIs(long ctx, final String name, final int cov, final MergeSemantics expectedMSem, final boolean generated) {
        assertThat(testJni.getCtxName(ctx), is(name));
        assertThat(testJni.getCtxCov(ctx), is(cov));
        int mSem = testJni.getCtxMergeSemantic(ctx);
        MergeSemantics semantic = null;
        for (MergeSemantics potential : MergeSemantics.values()) {
            if (potential.getTypeId() == mSem) {
                semantic = potential;
                break;
            } 
        }
        assertThat(semantic, is(expectedMSem));
        assertThat(testJni.isGenerated(ctx), is(generated));
    }

    @Test
    public void shouldPassNameAndCoverage_whileRegistering() throws NoSuchFieldException, IllegalAccessException {
        PerfCtx foo = new PerfCtx("foo", 15, MergeSemantics.DUPLICATE);
        Field fieldCtxId = PerfCtx.class.getDeclaredField("ctxId");
        fieldCtxId.setAccessible(true);
        long ctxId = (long) fieldCtxId.get(foo);
        assertCtxIs(ctxId, "foo", 15, MergeSemantics.DUPLICATE, false);
    }

    @Test
    public void shouldNotAllowInvalidValuesForCoverage() {
        try {
            new PerfCtx("foo", 101);
            fail("PerfCtx creation should have failed");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("Coverage-percentage value 101 is not valid, a valid value must be in the range [0, 100]."));
        }

        try {
            new PerfCtx("foo", -1);
            fail("PerfCtx creation should have failed");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("Coverage-percentage value -1 is not valid, a valid value must be in the range [0, 100]."));
        }
    }

    @Test
    public void shouldNotAllow_RestrictedChars_InCtxName() {
        for (String s : new String[] {"~", "%", "<", ">"}) {
            try {
                new PerfCtx("foo" + s, 10);
                fail("PerfCtx creation should have failed");
            } catch (IllegalArgumentException e) {
                assertThat(e.getMessage(), is(String.format("Name 'foo%s' has invalid character(s), chars '%%' and '~' are not allowed.", s)));
            }
        }
        
        for (String s : new String[] {",", ".", " ", "!", "@", "#", "$", "^", "&", "(", ")", "[", "]", "{", "}", "/", "'", "\"", ";", ":", "?"}) {
            try {
                new PerfCtx("foo" + s, 10);
            } catch (IllegalArgumentException e) {
                fail("PerfCtx creation should NOT have failed");
            }
        }
    }
    
    @Test
    public void shouldEnforceThatNameStartsWith_AnAlphaNumChar() {
        try {
            new PerfCtx("#foo", 10);
            fail("PerfCtx creation should have failed");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is(String.format("Name '#foo' has an invalid starting character, first-char must be alpha-numeric.")));
        }
        
        try {
            new PerfCtx(" ", 10);
            fail("PerfCtx creation should have failed");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is(String.format("Name ' ' has an invalid starting character, first-char must be alpha-numeric.")));
        }
        
        try {
            new PerfCtx("Foo", 10);
        } catch (IllegalArgumentException e) {
            fail("PerfCtx creation should NOT have failed");
        }
        
        try {
            new PerfCtx("10th Ctx", 10);
        } catch (IllegalArgumentException e) {
            fail("PerfCtx creation should NOT have failed");
        }
    }
    
    @Test
    public void shouldTrackContext_whenUsedIn_TryWithResources_Style() {
        //testJni.getAndStubCtxIdStart(105);
        PerfCtx ctx = new PerfCtx("foo bar baz", 25);
        long[] ctxs = new long[5];
        assertThat(testJni.getCurrentCtx(ctxs), is(0));
        try(ClosablePerfCtx x = ctx.open()) {
            assertThat(testJni.getCurrentCtx(ctxs), is(1));
            assertCtxIs(ctxs[0], "foo bar baz", 25, MergeSemantics.MERGE_TO_PARENT, false);            
        }
        assertThat(testJni.getCurrentCtx(ctxs), is(0));
    }
    
    @Test
    public void shouldPrintUseful_ToString() {
        PerfCtx ctx = new PerfCtx("foo bar baz", 25);
        assertThat(ctx.toString(), is("PerfCtx(1801439850948198402) {name: 'foo bar baz', coverage: 25%, merge_semantics: 'MergeSemantics{typeId=0} MERGE_TO_PARENT'}"));
        ClosablePerfCtx open = ctx.open();
        assertThat(open.toString(), is("ClosablePerfCtx(1801439850948198402) {name: 'foo bar baz', coverage: 25%, merge_semantics: 'MergeSemantics{typeId=0} MERGE_TO_PARENT'}"));
    }
    
    @Test
    public void shouldUnderstandEqualityIn_ClosablePerfCtx_Interface() {
        PerfCtx ctx = new PerfCtx("foo bar baz", 25);
        ClosablePerfCtx open = ctx.open();
        PerfCtx ctx1 = new PerfCtx("foo bar baz", 25);
        ClosablePerfCtx open1 = ctx1.open();
        assertThat(open, is(open1));
        open.close();
        assertThat(open, is(open1));
        open1.close();
        assertThat(open, is(open1));
    }
    
    @Test
    public void shouldDefaultParams_sensibly() {
        PerfCtx foo = new PerfCtx("foo");
        assertThat(foo.toString(), is("PerfCtx(720575940379279362) {name: 'foo', coverage: 10%, merge_semantics: 'MergeSemantics{typeId=0} MERGE_TO_PARENT'}"));
    }
}
