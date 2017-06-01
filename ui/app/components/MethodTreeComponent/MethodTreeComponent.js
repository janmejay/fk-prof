import React, { Component } from 'react';
import StacklineDetail from 'components/StacklineDetailComponent';
import StacklineStats from 'components/StacklineStatsComponent';
import { withRouter } from 'react-router';
import { ScrollSync, AutoSizer, Grid } from 'react-virtualized';
import memoize from 'utils/memoize';
import debounce from 'utils/debounce';

import styles from './MethodTreeComponent.css';
import HotMethodNode from '../../pojos/HotMethodNode';
import 'react-virtualized/styles.css';

//TODO: use monospace font for stack line to simplify width calculation

const noop = () => {};
const filterPaths = (pathSubset, k) => k.indexOf(pathSubset) === 0;

//Input is expected to be Array of (nodeIndex, callCount)   and
//output returned is an array of objects of type HotMethodNode
//This function aggregates nodes with same name+lineNo to be rendered and same name for first layer
//in hotmethodView. As part of aggregation their sampledCallCounts are added and respective parents added in a list
//with sampledCallCounts caused by them
const dedupeNodes = (allNodes) => (nodesWithCallCount) => {
  let dedupedNodes = {};
  for(let i=0; i<nodesWithCallCount.length; i++){
    let nodeWithCallCount = nodesWithCallCount[i];
    const nodeIndex = nodeWithCallCount[0];
    const node = allNodes[nodeIndex];
    const sampledCallCount = nodeWithCallCount[1];
    if(! node.hasParent()) break;
    let renderNode;
    if(sampledCallCount === undefined)
      renderNode = new HotMethodNode(true, node.lineNo, node.name, node.onCPU, [[nodeIndex, node.onCPU]]);
    else
      renderNode = new HotMethodNode(false, node.lineNo, node.name, sampledCallCount, [[node.parent, sampledCallCount]]);
    const key = renderNode.identifier();
    if (!dedupedNodes[key]) {
      dedupedNodes[key] = renderNode;
    } else {
      dedupedNodes[key].sampledCallCount += renderNode.sampledCallCount;
      dedupedNodes[key].parentsWithSampledCallCount = [...dedupedNodes[key].parentsWithSampledCallCount, ...renderNode.parentsWithSampledCallCount];
    }
  }
  return Object.keys(dedupedNodes).map(k => dedupedNodes[k]).sort((a, b) => b.sampledCallCount - a.sampledCallCount);
};

const getTextWidth = function(text, font) {
    // re-use canvas object for better performance
    var canvas = getTextWidth.canvas || (getTextWidth.canvas = document.createElement("canvas"));
    var context = canvas.getContext("2d");
    context.font = font;
    var metrics = context.measureText(text);
    return metrics.width;
}

class MethodTreeComponent extends Component {
  constructor (props) {
    super(props);
    this.state = {
      itemCount: 0,
      highlighted: {}, // keeps track of all highlighted nodes
    };

    this.containerWidth = 0;
    this.opened = {}; // keeps track of all opened/closed nodes

    this.stacklineDetailCellRenderer = this.stacklineDetailCellRenderer.bind(this);
    this.stacklineStatCellRenderer = this.stacklineStatCellRenderer.bind(this);
    this.getMaxWidthOfRenderedStacklines = this.getMaxWidthOfRenderedStacklines.bind(this);
    this.toggle = this.toggle.bind(this);
    this.handleFilterChange = this.handleFilterChange.bind(this);
    this.highlight = this.highlight.bind(this);
    this.debouncedHandleFilterChange = debounce(this.handleFilterChange, 250);
    this.getRenderedDescendantCountForListItem = this.getRenderedDescendantCountForListItem.bind(this);
    this.getRenderedChildrenCountForListItem = this.getRenderedChildrenCountForListItem.bind(this);
    this.isNodeHavingChildren = this.isNodeHavingChildren.bind(this);

    this.dedupeNodes = dedupeNodes(props.allNodes);
    this.renderData = this.getInitialRenderData(props.location.query[props.filterKey]);
    this.state.itemCount = this.renderData.length;

    this.setup(props.containerWidth); 
  }

  componentWillUpdate(nextProps, nextState) {
    this.setup(nextProps.containerWidth);
  }

  setup(containerWidth) {
    if(containerWidth > 0 && containerWidth !== this.containerWidth) {
      this.containerWidth = containerWidth;
    }
  }

  getMaxWidthOfRenderedStacklines() {
    let maxWidthOfRenderedStacklines = 0;
    for(let i = 0;i < this.renderData.length;i++) {
      if (maxWidthOfRenderedStacklines < this.renderData[i][4]) {
        maxWidthOfRenderedStacklines = this.renderData[i][4];
      }
    }
    maxWidthOfRenderedStacklines += 10; //added some buffer
    const minGridWidth = this.containerWidth - 165;
    return maxWidthOfRenderedStacklines < minGridWidth ? minGridWidth : maxWidthOfRenderedStacklines;
  }

  highlight (path) {
    if (path in this.state.highlighted) {
      const state = Object.assign({}, this.state);
      delete state.highlighted[path];
      this.setState(state);
      return;
    }
    // so no exact path matches
    // what if click was on a parent node
    const partialMatchedPaths = Object.keys(this.state.highlighted)
      .filter(filterPaths.bind(null, path));

    if (partialMatchedPaths.length) {
      // delete the partial matches from state,
      // so that new tree would get highlighted
      let state = Object.assign({}, this.state);
      partialMatchedPaths.forEach((p) => {
        delete state.highlighted[p];
      });
      this.setState(state);
    }

    // all good, highlight!
    this.setState({
      highlighted: {
        ...this.state.highlighted,
        [path]: true,
      },
    });
  }

  handleFilterChange (e) {
    const { pathname, query } = this.props.location;
    this.props.router.push({ pathname, query: { ...query, [this.props.filterKey]: e.target.value } });
    this.renderData = this.getInitialRenderData(e.target.value);
    this.setState({
      itemCount: this.renderData.length
    });
  }

  getInitialRenderData(filterText) {
    const { nextNodesAccessorField } = this.props;
    let nodeIndexes;
    if (nextNodesAccessorField === 'parent') {
      nodeIndexes = this.props.nodeIndexes.map((nodeIndex) => [nodeIndex, undefined]);
    } else {
      nodeIndexes = this.props.nodeIndexes;
    }
    return this.getRenderData(nodeIndexes, filterText, '', false, 0);
  }

  render () {    
    const filterText = this.props.location.query[this.props.filterKey];
    console.log("render of methodtree called", this.containerWidth, filterText);
    
    const { nextNodesAccessorField } = this.props;
    if(this.containerWidth == 0) {
      return null;
    }
    let containerHeight = window.innerHeight - 270; //subtracting height of everything above the container
    let gridHeight = containerHeight - 87; //subtracting height of filter box
    return (
      <div style={{display: "flex", width: this.containerWidth}}>
        <div style={{flex: "1 1 auto", height: containerHeight + "px"}}>
          <ScrollSync>
            {({ clientHeight, clientWidth, onScroll, scrollHeight, scrollLeft, scrollTop, scrollWidth }) => (
              <div className={styles.GridRow}>
                <div className={styles.LeftGridContainer}>
                  <div className={styles.GridHeader}>
                    <div className={`mdl-textfield mdl-js-textfield ${styles.filterBox}`}>
                      <label htmlFor="method_filter">
                        {nextNodesAccessorField === 'parent' ? "Filter hot methods" : "Filter root callers"}
                      </label>
                      <input
                        className={`mdl-textfield__input`}
                        type="text"
                        defaultValue={filterText}
                        autoFocus
                        onChange={this.debouncedHandleFilterChange}
                        id="method_filter"
                      />
                    </div>
                  </div>
                  <div className={styles.GridBody}>
                    <AutoSizer disableHeight>
                      {({ width }) => (
                        <Grid
                          columnCount={1}
                          columnWidth={this.getMaxWidthOfRenderedStacklines()}
                          height={gridHeight}
                          width={width}
                          rowCount={this.state.itemCount}
                          rowHeight={25}
                          cellRenderer={this.stacklineDetailCellRenderer}
                          className={styles.LeftGrid}
                          overscanRowCount={2}
                          onScroll={onScroll}
                        />
                      )}
                    </AutoSizer>
                  </div>
                </div>
                <div className={styles.RightGridContainer}>
                  <div className={styles.GridHeader}>
                    <label>Samples</label>
                  </div>
                  <div className={styles.GridBody}>
                    <Grid
                      columnCount={1}
                      columnWidth={150}
                      height={gridHeight}
                      width={150}
                      rowCount={this.state.itemCount}
                      rowHeight={25}
                      cellRenderer={this.stacklineStatCellRenderer}
                      className={styles.RightGrid}
                      overscanRowCount={2}
                      scrollTop={scrollTop}
                    />
                  </div>
                </div>
              </div>
            )}
          </ScrollSync>
        </div>
        {!this.state.itemCount && (
          <p className={styles.alert}>No results</p>
        )}
      </div>
    );
  }

  stacklineDetailCellRenderer (params) {
    let newstyle = {display: "flex", flexDirection: "row", alignItems: "center"};
    let rowstyle = Object.assign({}, params.style);
    //rowstyle.width = "auto";
    // rowstyle.position = "relative";
    //rowstyle.right = "0px";
    rowstyle.height = "25px";
    rowstyle.whiteSpace = "nowrap";
    let rowdata = this.renderData[params.rowIndex];
    let n = rowdata[1], uniqueId = rowdata[0];

    //TODO: optimize, move below assignment to lifecycle method when properties are received by component
    const percentageDenominator = (this.props.allNodes.length > 0) ? this.props.allNodes[0].onStack : 1;

    let countToDisplay, newNodeIndexes;
    let displayName = this.props.methodLookup[n.name][0];
    let displayNameWithArgs = this.props.methodLookup[n.name][0] + this.props.methodLookup[n.name][1];

    //This condition is equivalent to (n instanceOf HotMethodNode)
    //since nextNodesAccessorField is = parent in hot method view and
    //Node type for dedupedNodes is HotMethodNode from above
    if (this.props.nextNodesAccessorField === 'parent') {
      newNodeIndexes = n.parentsWithSampledCallCount;
      const lineNoOrNot = (n.belongsToTopLayer)? '' : ':' + n.lineNo;
      displayName = displayName + lineNoOrNot;
      displayNameWithArgs = displayNameWithArgs + lineNoOrNot;
      countToDisplay = n.sampledCallCount;
    } else {
      // using the index i because in call tree the name of sibling nodes
      // can be same, react will throw up, argh!
      newNodeIndexes = n.children;
      displayName = displayName + ':' + n.lineNo;
      displayNameWithArgs = displayNameWithArgs + ':' + n.lineNo;
      countToDisplay = n.onStack;
    }
    const onStackPercentage = Number((countToDisplay * 100) / percentageDenominator).toFixed(2);
    const isHighlighted = Object.keys(this.state.highlighted)
      .filter(filterPaths.bind(null, uniqueId));

    return (
        <StacklineDetail
          key={uniqueId}
          style={{...params.style, height: 25, whiteSpace: 'nowrap'}}
          nodename={displayNameWithArgs}
          listIdx={params.rowIndex}
          stackline={displayName}
          indent={rowdata[2]}
          nodestate={this.opened[uniqueId]}
          highlight={isHighlighted.length}
          subdued={rowdata[3] == 1 ? true : false}
          onHighlight={noop}
          onClick={newNodeIndexes ? this.toggle.bind(this, params.rowIndex) : noop}>
        </StacklineDetail> 
      
    );
  }

  stacklineStatCellRenderer({ columnIndex, key, parent, rowIndex, style }) {
    let rowdata = this.renderData[rowIndex];
    let n = rowdata[1], uniqueId = rowdata[0];

    //TODO: optimize, move below assignment to lifecycle method when properties are received by component
    const percentageDenominator = (this.props.allNodes.length > 0) ? this.props.allNodes[0].onStack : 1;
    let countToDisplay, newNodeIndexes;

    //This condition is equivalent to (n instanceOf HotMethodNode)
    //since nextNodesAccessorField is = parent in hot method view and
    //Node type for dedupedNodes is HotMethodNode from above
    if (this.props.nextNodesAccessorField === 'parent') {
      countToDisplay = n.sampledCallCount;
    } else {
      countToDisplay = n.onStack;
    }
    const onStackPercentage = Number((countToDisplay * 100) / percentageDenominator).toFixed(2);
    const isHighlighted = Object.keys(this.state.highlighted)
      .filter(filterPaths.bind(null, uniqueId));
    return (
      <StacklineStats
        key={uniqueId}
        style={style}
        samples={countToDisplay}
        samplesPct={onStackPercentage}
        highlight={isHighlighted.length}
        subdued={rowdata[3] == 1 ? true : false}>
      </StacklineStats>
    );
  }

  toggle (listIdx) {
    const rowdata = this.renderData[listIdx];
    const uniqueId = rowdata[0];

    let nodeIndexes;
    if (this.props.nextNodesAccessorField === 'parent') {
      nodeIndexes = rowdata[1].parentsWithSampledCallCount;
    } else {
      nodeIndexes = rowdata[1].children;
    }

    if(!this.opened[uniqueId]) {
      //expand
      var childRenderData = this.getRenderData(nodeIndexes, null, uniqueId, rowdata[3], rowdata[2]);
      var postarr = this.renderData.splice(listIdx + 1);
      this.renderData = this.renderData.concat(childRenderData, postarr);            
    } else {
      //collapse
      const descendants = this.getRenderedDescendantCountForListItem(listIdx);
      if(descendants > 0) {
        this.renderData.splice(listIdx + 1, descendants);
      }
    }
    this.opened[uniqueId] = !this.opened[uniqueId];
    this.setState({
      itemCount: this.renderData.length
    });
  }

  getRenderedDescendantCountForListItem(listIdx) {
    let currIdx = listIdx;
    let toVisit = this.getRenderedChildrenCountForListItem(currIdx);
    while(toVisit > 0) {
      toVisit--;
      currIdx++;
      toVisit += this.getRenderedChildrenCountForListItem(currIdx);
    }
    return currIdx - listIdx;
  }

  getRenderedChildrenCountForListItem(listIdx) {
    let children = 0;
    let rowdata = this.renderData[listIdx];
    if(rowdata) {
      const uniqueId = rowdata[0];
      if(this.opened[uniqueId]) {
        if(this.isNodeHavingChildren(rowdata[1])) {
          //At least one rendered child item is going to be present for this item
          //Cannot rely on childNodeIndexes(calculated in isNodeHavingChildren method) to get count of children because actual rendered children can be lesser after deduping of nodes for hot method tree          
          let child_rowdata = this.renderData[listIdx + 1];
          if(child_rowdata) {
            return child_rowdata[3];
          } else {
            console.error("This should never happen. If list item is expanded and its childNodeIndexes > 0, then at least one more item should be present in renderdata list")
          }
        }
      }
    }
    return children;
  }

  isNodeHavingChildren(node) {
    if(this.props.nextNodesAccessorField === 'parent') {
      let childNodeIndexes = node.parentsWithSampledCallCount;
      if(childNodeIndexes && childNodeIndexes.length > 0) {
        if(childNodeIndexes.length != 1) {
          return true;
        } else {
          //If this has only one childnodeindex and that is "0" node => corresponds to root node which is not rendered in UI
          //"if(! node.hasParent()) break;" condition in dedupeNodes method ensure above node is not rendered
          if(childNodeIndexes[0][0] === 0) {
            return false;
          } else {
            return true;
          }
        }
      } else {
        return false;
      }
    } else {
      return (node.children && node.children.length > 0);
    }
  }

  getRenderData (nodeIndexes = [], filterText, parentPath, parentHasSiblings, parentIndent) {
    const renderStack = [];
    renderStack.push({
      p_pth: parentPath, //parent path
      gen: {
        nis: nodeIndexes, //indexes of first-level nodes in the tree subject to de-duplication
        p_ind: parentIndent, //indentation of parent node
        p_sib: parentHasSiblings, //parentHasSiblings
      }
    });

    const percentageDenominator = (this.props.allNodes.length > 0) ? this.props.allNodes[0].onStack : 1;
    const renderData = [];

    while(renderStack.length > 0) {
      let se = renderStack.pop();
      if (se.gen) {
        // only need to de-dupe for bottom-up not top-down,
        // hence the ternary :/
        const dedupedNodes = this.props.nextNodesAccessorField === 'parent'
          ? this.dedupeNodes(se.gen.nis)
          : se.gen.nis.map((nodeIndex) => this.props.allNodes[nodeIndex]).slice().sort((a, b) => b.onStack - a.onStack);

        //Indent should always be zero if no parent
        //Otherwise, if parent has siblings or if this node has siblings, do a major indent of the nodes, minor indent otherwise
        const indent = !se.p_pth ? 0 : ((se.gen.p_sib || dedupedNodes.length > 1 ) ? se.gen.p_ind + 10 : se.gen.p_ind + 3);
        renderStack.push({
          p_pth: se.p_pth,
          node: {
            dn: dedupedNodes, //first-level nodes
            ind: indent, //indentation to be applied to rendered node
            idx: 0, //index in array "dn" to identify the node to render,
          }
        });
      } else {
        if(se.node.idx >= se.node.dn.length) {
          continue;
        }
        const n = se.node.dn[se.node.idx];
        //Node has been retrieved so it is safe to increment index and push stack entry back in render stack
        //Fields from this entry will be read further in this iteration but not modified beyond this point, avoiding un-necessary object copy
        se.node.idx++;
        //After index increment, stack entry refers to next sibling, pushing it now itself on stack
        //This ensures that stack entries of children of the current node are pushed later and hence processed earlier
        renderStack.push(se);

        let displayName = this.props.methodLookup[n.name][0];
        //If this is a first-level node(p_pth will be empty) and filter is applied, skip rendering of node if display name does not match the filter
        if(filterText && !se.p_pth && !displayName.match(new RegExp(filterText, 'i'))) {
          continue;
        }

        let uniqueId, newNodeIndexes;
        //This condition is equivalent to (n instanceOf HotMethodNode)
        //since nextNodesAccessorField is = parent in hot method view and
        //Node type for dedupedNodes is HotMethodNode from above
        if (this.props.nextNodesAccessorField === 'parent') {
          uniqueId = `${se.p_pth}->${n.identifier()}`;
          newNodeIndexes = n.parentsWithSampledCallCount;
          const lineNoOrNot = (n.belongsToTopLayer)? '' : ':' + n.lineNo;
          displayName = displayName + lineNoOrNot;
        } else {
          // using the index i because in call tree the name of sibling nodes
          // can be same, react will throw up, argh!
          uniqueId = `${se.p_pth}->${n.name}:${n.lineNo}`;
          newNodeIndexes = n.children;
          displayName = displayName + ':' + n.lineNo;
        }
    
        // If only single node is being rendered, expand the node
        // Or if the node has no children, then expand the node, so that expanded icon is rendered against this node
        if(se.node.dn.length == 1 || newNodeIndexes.length == 0) {
          this.opened[uniqueId] = true;
        }

        const nodeData = [uniqueId, n, se.node.ind, se.node.dn.length, getTextWidth(displayName, "14px Arial") + 28 + se.node.ind];
        renderData.push(nodeData);

        if(this.opened[uniqueId] && newNodeIndexes) {
          renderStack.push({
            p_pth: uniqueId,
            gen: {
              nis: newNodeIndexes,
              p_ind: se.node.ind,
              p_sib: se.node.dn.length > 1
            }
          });
        }
      }
    }
    return renderData;
  }
}

MethodTreeComponent.propTypes = {
  allNodes: React.PropTypes.array,
  nodeIndexes: React.PropTypes.array,
  nextNodesAccessorField: React.PropTypes.string.isRequired,
  methodLookup: React.PropTypes.array,
  filterKey: React.PropTypes.string
};

export default withRouter(MethodTreeComponent);