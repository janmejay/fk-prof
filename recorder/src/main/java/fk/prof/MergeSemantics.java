package fk.prof;

/**
 * @understands how to combine perf-data recorded when perf-ctx nests
 * <p>
 * If a parent doesn't exist, this has no relevance, because merge isn't relevant in absence of a parent. This also implies that merge-semantics should always be seen from the most-recently-entered-ctx or child-ctx PoV.
 */
public enum MergeSemantics {
    /*
      Coverage implications:
      
      Meaning of coverage changes depending on the chosen merge semantic for a particular ctx. Eg. if we enter ctx P followed by Q, and if both has 10% coverage,
      it doesn't make sense to double-filter, because 1/10 * 1/10 = 1/100, so for scoped capture "P > Q" user will have to understand that only 1% coverage is being reported. This is confusing at the best and useless in the worst case. 
      So instead of double-filtering, we directly control filtering in a sane/least-surprise way, by auto-choosing filtering mode depending on the user-chosen merge-semantic. There are 2 filtering modes, we document the chosen mode inline with enum constant.
      Filter-mode:
      FM_parent - Ignore coverage value specified by child, simply record as if parent-ctx is directly controlling filtering for child. In this case, child records if parent records. However, when the same context appears as parent-context by itself, coverage value is respected.
      FM_child - Ignore coverage value specified by the parent, simply record as if parent-ctx doesn't exist.
     */

    /**
     * Data from this ctx will me merged into its wrapper/parent and to user it'll appear as if child doesn't exist.
     * <p>
     * Filter: FM_parent
     */
    MERGE_TO_PARENT(0),

    /**
     * Child ctx name is modified to reflect the wrapping ctx. Eg. when a control from parent ctx "P" enters a child ctx "Q", data in Q is recorded as "P > Q", but this data doesn't get recorded in Q or in P.
     * <p>
     * Filter: FM_parent
     * <p>
     * WARNING: No more than PerfCtx.MAX_PARENT_SCOPE_NESTING contexts can be merged this way. Performance-data beyond supported levels of nesting MAY be folded in without scoping. Eg. nesting is 7-node deep and of the form A -> B -> C -> D -> E ->F -> G, data for G MAY be folded into scoped-context ending in E. Also, note that its easy to violate the upper-limit on number of contexts to be tracked using this merge-semantic, which leads to data-loss, so this must be used with care.
     */
    PARENT_SCOPED(1),

    /**
     * Similar to PARENT_SCOPED, but will scope for itself too. Eg. When parent ctx "Q" enters itself again, this creates context "Q > Q".
     * <p>
     * Filter: FM_parent
     * <p>
     * WARNING: This can create similar set of problems as PARENT_SCOPED merge semantic.   
     */
    PARENT_SCOPED_STRICT(2),
    
    /**
     * Ctx behaves like a stack (FIFO semantics). The recorded data goes to the closest ctx and is invisible in wrapper/parent ctx(s).
     * <p>
     * Filter: FM_child
     */
    STACK_UP(3),

    /**
     * Records data under current "and" all parent scopes that choose this merge-semantic. This means, in example above, the data will appear twice in "P" and in "Q". There is no way to distinguish data that comes from P -> Q nesting vs P -> R -> S -> Q nesting vs direct call to Q.
     * <p>
     * Filter: FM_child
     */
    DUPLICATE(4);

    private final int typeId;

    MergeSemantics(int typeId) {
        this.typeId = typeId;
    }

    @Override
    public String toString() {
        return "MergeSemantics{" +
                "typeId=" + typeId +
                "} " + super.toString();
    }

    public int getTypeId() {
        return typeId;
    }
}
