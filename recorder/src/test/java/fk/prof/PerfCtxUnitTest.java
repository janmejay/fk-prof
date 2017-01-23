package fk.prof;

import org.junit.Before;
import org.junit.Test;

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
    }

    @Test
    public void shouldUnderstandEquality_purelyAsAFunctionOf_CtxId() {
        testJni.getAndStubCtxIdStart(10);
        PerfCtx foo = new PerfCtx("foo", 10);
        testJni.getAndStubCtxIdStart(10);
        PerfCtx bar = new PerfCtx("bar", 15);
        assertThat(foo, is(bar));
        PerfCtx baz = new PerfCtx("foo", 10);
        assertThat(foo, not(baz));
    }

    @Test
    public void shouldTrackCtx() {
        testJni.getAndStubCtxIdStart(4);
        PerfCtx foo = new PerfCtx("foo", 15);
        assertThat(testJni.getCurrentCtx(), is(-1));
        foo.begin();
        assertThat(testJni.getCurrentCtx(), is(4));
        foo.end();
        assertThat(testJni.getCurrentCtx(), is(-1));
    }

    @Test
    public void shouldPassNameAndCoverage_whileRegistering() {
        testJni.getAndStubCtxIdStart(8);
        PerfCtx foo = new PerfCtx("foo", 15);
        assertThat(testJni.getLastRegisteredCtxName(), is("foo"));
        assertThat(testJni.getLastRegisteredCtxCoveragePct(), is(15));
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
        testJni.getAndStubCtxIdStart(105);
        PerfCtx ctx = new PerfCtx("foo bar baz", 25);
        assertThat(testJni.getCurrentCtx(), is(-1));
        try(ClosablePerfCtx x = ctx.open()) {
            assertThat(testJni.getCurrentCtx(), is(105));            
        }
        assertThat(testJni.getCurrentCtx(), is(-1));
    }
    
    @Test
    public void shouldPrintUseful_ToString() {
        testJni.getAndStubCtxIdStart(42);
        PerfCtx ctx = new PerfCtx("foo bar baz", 25);
        assertThat(ctx.toString(), is("PerfCtx(42) {name: 'foo bar baz', coverage: 25%}"));
        ClosablePerfCtx open = ctx.open();
        assertThat(open.toString(), is("ClosablePerfCtx(42) {name: 'foo bar baz', coverage: 25%}"));
    }
    
    @Test
    public void shouldUnderstandEqualityIn_ClosablePerfCtx_Interface() {
        testJni.getAndStubCtxIdStart(10);
        PerfCtx ctx = new PerfCtx("foo bar baz", 25);
        ClosablePerfCtx open = ctx.open();
        testJni.getAndStubCtxIdStart(10);
        PerfCtx ctx1 = new PerfCtx("foo bar baz", 25);
        ClosablePerfCtx open1 = ctx.open();
        assertThat(open, is(open1));
        open.close();
        assertThat(open, is(open1));
        open1.close();
        assertThat(open, is(open1));
    }
    
    @Test
    public void shouldDefaultParams_sensibly() {
        testJni.getAndStubCtxIdStart(95);
        PerfCtx foo = new PerfCtx("foo");
        assertThat(foo.toString(), is("PerfCtx(95) {name: 'foo', coverage: 10%, merge_semantics: 'MergeSemantics{typeId=0} MERGE_TO_PARENT'}"));
    }
}
