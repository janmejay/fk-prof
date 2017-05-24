import React, { Component } from 'react';
import StackTreeElement from 'components/StackTreeElementComponent';
import { withRouter } from 'react-router';
import { List, AutoSizer, WindowScroller } from 'react-virtualized';
import memoize from 'utils/memoize';
import debounce from 'utils/debounce';

import styles from './MethodTreeComponent.css';
import treeStyles from 'components/StackTreeElementComponent/StackTreeElementComponent.css';
import HotMethodNode from '../../pojos/HotMethodNode';
import 'react-virtualized/styles.css';

//TODO: eval and remove childautoexpand, autoexpand later if unnecessary
//TODO: remove memoize related code
//TODO: optimize elements of this.renderdata, see if some entries can be removed/computed

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

const stringifierFunction = function (a) {
  if (Array.isArray(a)) return a[0]+":"+a[1];
  if (Number.isInteger(a)) return a;
  return a.name;
};

class MethodTreeComponent extends Component {
  constructor (props) {
    super(props);
    this.opened = {}; // keeps track of all opened/closed nodes
    this.state = {
      itemCount: 0,
      highlighted: {}, // keeps track of all highlighted nodes
    };
    this.rowRenderer = this.rowRenderer.bind(this);
    this.listRef = null;
    this.setListRef = this.setListRef.bind(this);
    this.userToggledNode = null;
    this.toggle = this.toggle.bind(this);
    this.handleFilterChange = this.handleFilterChange.bind(this);
    this.highlight = this.highlight.bind(this);
    this.debouncedHandleFilterChange = debounce(this.handleFilterChange, 250);
    // this.memoizedDedupeNodes = memoize(
    //   dedupeNodes(props.allNodes), stringifierFunction, true);
    this.dedupeNodes = dedupeNodes(props.allNodes);

    this.renderData = this.getInitialRenderData();
    this.state.itemCount = this.renderData.length;
    this.getRenderedDescendantCountForListItem = this.getRenderedDescendantCountForListItem.bind(this);
    this.getRenderedChildrenCountForListItem = this.getRenderedChildrenCountForListItem.bind(this);
    this.isNodeHavingChildren = this.isNodeHavingChildren.bind(this);
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

  // toggle (open) {
  //   this.userToggledNode = open;
  //   this.setState({
  //     opened: {
  //       ...this.state.opened,
  //       [open]: !this.state.opened[open],
  //     },
  //   });
  // }

  handleFilterChange (e) {
    const { pathname, query } = this.props.location;
    this.props.router.push({ pathname, query: { ...query, [this.props.filterKey]: e.target.value } });
  }

  getInitialRenderData() {
    const filterText = this.props.location.query[this.props.filterKey];
    const { nextNodesAccessorField } = this.props;
    let nodeIndexes;
    if (nextNodesAccessorField === 'parent') {
      nodeIndexes = this.props.nodeIndexes.map((nodeIndex) => [nodeIndex, undefined]);
    } else {
      nodeIndexes = this.props.nodeIndexes;
    }
    return this.getRenderData(nodeIndexes, filterText, '', false, 0, true);
  }

  render () {    
    console.log("render of methodtree called");
    return (
      <div style={{display: "flex"}}>
        <div style={{flex: "1 1 auto", height: "400px"}}>
          
              <List
                ref={this.setListRef}
                width={500}
                height={400}
                rowCount={this.state.itemCount}
                rowHeight={25}
                rowRenderer={this.rowRenderer}
                className={treeStyles.container}
                containerStyle={{overflowX: "auto", width: "2000px", maxWidth: "2000px"}}
                overscanRowCount={2}
              />
            
        </div>
        {!this.state.itemCount && (
          <p className={styles.alert}>No results</p>
        )}
      </div>
    );
  }

  rowRenderer (params) {
    let newstyle = {display: "flex", flexDirection: "row", alignItems: "center"};
    let rowstyle = Object.assign({}, params.style);
    rowstyle.width = "auto";
    // rowstyle.position = "relative";
    rowstyle.right = "0px";
    let rowdata = this.renderData[params.index];
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
      <StackTreeElement
        nodename={displayNameWithArgs}
        key={uniqueId}
        listIdx={params.index}
        style={rowstyle}
        stackline={displayName}
        samples={countToDisplay}
        samplesPct={onStackPercentage}
        indent={rowdata[2]}
        nodestate={this.opened[uniqueId]}
        highlight={isHighlighted.length}
        subdued={rowdata[3] == 1 ? true : false}
        onHighlight={noop}
        onClick={newNodeIndexes ? this.toggle.bind(this, params.index) : noop}>
      </StackTreeElement> 
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
      var childRenderData = this.getRenderData(nodeIndexes, null, uniqueId, rowdata[3], rowdata[2], rowdata[4]);
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

  getRenderData (nodeIndexes = [], filterText, parentPath, parentHasSiblings, parentIndent, autoExpand) {
    const renderStack = [];
    renderStack.push({
      ae: autoExpand, //autoExpand behaviour
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
          ae: se.ae,
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
        } else {
          // using the index i because in call tree the name of sibling nodes
          // can be same, react will throw up, argh!
          uniqueId = `${se.p_pth}->${n.name}:${n.lineNo}`;
          newNodeIndexes = n.children;
        }

        //By default, keep auto expand behavior of children same as parent.
        //As a special case, if this is the node toggled by user and it was expanded, then enable auto expand in the children of this node
        let childAutoExpand = se.ae;
        // console.log("childautoexpand", this.userToggledNode, uniqueId, this.opened[uniqueId]);
        // if(this.userToggledNode && this.userToggledNode == uniqueId && this.opened[uniqueId]) {
        //   childAutoExpand = true;
        // }
        // Following condition should always be evaluated after the childAutoExpand is set, since this mutates this.state.opened[uniqueId]
        // If auto expand behavior is enabled and only single node is being rendered, expand the node
        // Or if the node has no children, then expand the node, so that expanded icon is rendered against this node
        if((se.ae && se.node.dn.length == 1) || newNodeIndexes.length == 0) {
          this.opened[uniqueId] = true;
        }

        const nodeData = [uniqueId, n, se.node.ind, se.node.dn.length, childAutoExpand];
        renderData.push(nodeData);

        if(this.opened[uniqueId] && newNodeIndexes) {
          renderStack.push({
            ae: childAutoExpand,
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

  setListRef (ref) {
    this.listRef = ref;
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


/*
<WindowScroller>
            {({ height, isScrolling, scrollTop }) => (
              <AutoSizer disableHeight>
                {({ width }) => (
                  <List
                    autoHeight
                    width={width}
                    height={height}
                    isScrolling={isScrolling}
                    scrollTop={scrollTop}
                    rowCount={this.renderData.length}
                    rowHeight={25}
                    rowRenderer={this.rowRenderer}
                    overscanRowCount={2}
                  />
                )}
              </AutoSizer>
            )}
          </WindowScroller>
          */

/*
<AutoSizer >
            {({ width, height }) => (
              <List
                ref={this.setListRef}
                width={width}
                height={height}
                rowCount={this.state.itemCount}
                rowHeight={25}
                rowRenderer={this.rowRenderer}
                className={treeStyles.container}
                containerStyle={{"overflowX": "auto"}}
                overscanRowCount={2}
              />
            )}
          </AutoSizer>
          */