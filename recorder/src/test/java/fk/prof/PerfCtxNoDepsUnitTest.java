package fk.prof;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class PerfCtxNoDepsUnitTest {
    @Test
    public void shouldConsiderEverythingEqual() {
        PerfCtx foo = new PerfCtx("foo", 10);
        PerfCtx bar = new PerfCtx("bar", 15);
        assertThat(foo, is(bar));
        PerfCtx baz = new PerfCtx("foo", 10);
        assertThat(foo, is(baz));
    }

    @Test
    public void shouldNotFail_BeginAndEnd() {
        PerfCtx foo = new PerfCtx("foo", 15);
        foo.begin();
        foo.end();
    }

    @Test
    public void shouldNotFailToDisallow_InvalidValuesForCoverage() {
        try {
            new PerfCtx("foo", 101);
            fail("PerfCtx creation should have failed");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("Coverage-percentage value 101 is not valid, a valid value must be in the range [0, 100]."));
        }

        try {
            new PerfCtx("foo", -2);
            fail("PerfCtx creation should have failed");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("Coverage-percentage value -2 is not valid, a valid value must be in the range [0, 100]."));
        }
    }

    @Test
    public void shouldNotFailToDisallow_RestrictedChars_InCtxName() {
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
    public void shouldNotFailTo_EnforceThatNameStartsWith_AnAlphaNumChar() {
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
    public void shouldNotFail_whenUsedIn_TryWithResources_Style() {
        PerfCtx ctx = new PerfCtx("foo bar baz", 25);
        long t = 0;
        try(ClosablePerfCtx x = ctx.open()) {
            t = System.currentTimeMillis();
        }
        assertThat(t, not(0));
    }
    
    @Test
    public void shouldNotFailTo_PrintUseful_ToString() {
        PerfCtx ctx = new PerfCtx("foo bar baz", 25);
        assertThat(ctx.toString(), is("PerfCtx(0) {name: 'foo bar baz', coverage: 25%, merge_semantics: 'MergeSemantics{typeId=0} MERGE_TO_PARENT'}"));
        ClosablePerfCtx open = ctx.open();
        assertThat(open.toString(), is("ClosablePerfCtx(0) {name: 'foo bar baz', coverage: 25%, merge_semantics: 'MergeSemantics{typeId=0} MERGE_TO_PARENT'}"));
    }
    
    @Test
    public void shouldNotFailTo_UnderstandEqualityIn_ClosablePerfCtx_Interface() {
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
    public void shouldNotFailTo_DefaultParams_sensibly() {
        PerfCtx foo = new PerfCtx("foo");
        assertThat(foo.toString(), is("PerfCtx(0) {name: 'foo', coverage: 10%, merge_semantics: 'MergeSemantics{typeId=0} MERGE_TO_PARENT'}"));
    }
}
