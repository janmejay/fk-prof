import React, { Component } from 'react';
import TreeView from 'react-treeview';
import { withRouter } from 'react-router';
import memoize from 'utils/memoize';
import debounce from 'utils/debounce';

import 'react-treeview/react-treeview.css';
import styles from './MethodTreeComponent.css';
import HotMethodNode from '../../pojos/HotMethodNode';

const noop = () => {};
const filterPaths = (pathSubset, k) => k.indexOf(pathSubset) === 0;

//Input is expected to be [Array of (nodeIndex, callCount), pathInHotMethodView]   and
//output returned is an array of objects of type HotMethodNode
//This function aggregates nodes with same name+lineNo to be rendered and same name for first layer
//in hotmethodView. As part of aggregation their sampledCallCounts are added and respective parents added in a list
//with sampledCallCounts caused by them
const dedupeNodes = (allNodes) => (nodesWithPath) => {
  const nodesWithCallCount = nodesWithPath[0];
  const path = nodesWithPath[1];
  const depth = path.split("->").length-1;
  const dedupedNodes = nodesWithCallCount.reduce((accum, nodeWithCallCount) => {
    const nodeIndex = nodeWithCallCount[0];
    const node = allNodes[nodeIndex];
    const sampledCallCount = nodeWithCallCount[1];
    if(! node.hasParent()) return accum;

    let renderNode;
    if(depth === 0)
      renderNode = new HotMethodNode(true, node.lineNo, node.name, node.onCPU, [[nodeIndex, node.onCPU]]);
    else
      renderNode = new HotMethodNode(false, node.lineNo, node.name, sampledCallCount, [[node.parent, sampledCallCount]]);
    const key = renderNode.identifier();
    if (!accum[key]) {
      accum[key] = renderNode;
    } else {
      accum[key].sampledCallCount += renderNode.sampledCallCount;
      accum[key].parentsWithSampledCallCount = [...accum[key].parentsWithSampledCallCount, ...renderNode.parentsWithSampledCallCount];
    }
    return accum;
  }, {});
   return Object.keys(dedupedNodes).map(k => dedupedNodes[k]).sort((a, b) => b.sampledCallCount - a.sampledCallCount);
};

const stringifierFunction = function (a) {
  if (Array.isArray(a)) return a[0];
  if (Number.isInteger(a)) return a;
  return a.name;
};

class MethodTreeComponent extends Component {
  constructor (props) {
    super(props);
    this.state = {
      opened: {}, // keeps track of all opened/closed nodes
      highlighted: {}, // keeps track of all highlighted nodes
    };
    this.getTree = this.getTree.bind(this);
    this.toggle = this.toggle.bind(this);
    this.handleFilterChange = this.handleFilterChange.bind(this);
    this.highlight = this.highlight.bind(this);
    this.debouncedHandleFilterChange = debounce(this.handleFilterChange, 250);
    this.memoizedDedupeNodes = memoize(
      dedupeNodes(props.allNodes), stringifierFunction, true,
    );
  }


  getTree = (nodeIndexes = [], parentPath = '', filterText) => {
    // only need to de-dupe for bottom-up not top-down,
    // hence the ternary :/
    //TODO: Check if this memoization can be skipped with just using the isOpen state variable
    const dedupedNodes = this.props.nextNodesAccessorField === 'parent'
      ? this.memoizedDedupeNodes(nodeIndexes, parentPath)
      : nodeIndexes.map((nodeIndex) => this.props.allNodes[nodeIndex]).slice().sort((a, b) => b.onStack - a.onStack);

    const percentageDenominator = (this.props.allNodes.length > 0)? this.props.allNodes[0].onStack: 1;
    const dedupedTreeNodes = dedupedNodes.map((n, i) => {
      let uniqueId, newNodeIndexes, countToDisplay;
      let displayName = this.props.methodLookup[n.name];

      //This condition is equivalent to (n instanceOf HotMethodNode)
      //since nextNodesAccessorField is = parent in hot method view and
      //Node type for dedupedNodes is HotMothodNode from above
      if(this.props.nextNodesAccessorField === 'parent'){
        uniqueId = `${parentPath}->${n.identifier()}`;
        newNodeIndexes = n.parentsWithSampledCallCount;
        const lineNoOrNot = (n.belongsToTopLayer)? '' : ':' + n.lineNo;
        displayName = displayName + lineNoOrNot ;
        countToDisplay = n.sampledCallCount;
      }else{
        // using the index i because in call tree the name of sibling nodes
        // can be same, react will throw up, argh!
        uniqueId = `${i+parentPath}->${n.name}:${n.lineNo}`;
        newNodeIndexes = n.children;
        displayName = displayName + ':' + n.lineNo;
        countToDisplay = n.onStack;
      }
      const onStackPercentage = Number((countToDisplay * 100) / percentageDenominator).toFixed(2);
      const showDottedLine = parentPath && dedupedNodes.length >= 2 && dedupedNodes.length !== i + 1 &&
        this.state.opened[uniqueId];
      const isHighlighted = Object.keys(this.state.highlighted)
        .filter(filterPaths.bind(null, uniqueId));
      return (
        <TreeView
          data-nodename={displayName}
          itemClassName={`${styles.relative} ${styles.hover} ${showDottedLine ? 'dotted-line' : ''}`}
          key={uniqueId}
          defaultCollapsed={!(this.state.opened[uniqueId] && newNodeIndexes)}
          nodeLabel={
            <div className={styles.listItem}>
              <div
                className={`${styles.code} ${isHighlighted.length && styles.yellow}`}
                title={displayName}
                onClick={this.highlight.bind(this, uniqueId)}
              >
                {displayName}
              </div>
              { (
                <div className={`${styles.pill} ${styles.onStack}`}>
                  <div className={styles.number}>{countToDisplay}</div>
                  <div className={styles.percentage}>
                    <div className={styles.shade} style={{ width: `${onStackPercentage}%` }} />
                    {onStackPercentage}%
                  </div>
                </div>
              )}
            </div>
          }
          onClick={newNodeIndexes ? this.toggle.bind(this, uniqueId) : noop}
        >
          {
            this.state.opened[uniqueId] && newNodeIndexes && this.getTree(newNodeIndexes, uniqueId)
          }
        </TreeView>
      );
    });
    return filterText
      ? dedupedTreeNodes.filter(node => node.props['data-nodename'].match(new RegExp(filterText, 'i')))
      : dedupedTreeNodes;
  };

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

  toggle (open) {
    this.setState({
      opened: {
        ...this.state.opened,
        [open]: !this.state.opened[open],
      },
    });
  }

  handleFilterChange (e) {
    const { pathname, query } = this.props.location;
    this.props.router.push({ pathname, query: { ...query, filterText: e.target.value } });
  }

  render () {
    const { filterText } = this.props.location.query;
    const { nextNodesAccessorField } = this.props;
    let nodeIndexes;
    if(nextNodesAccessorField === 'parent') {
      nodeIndexes = this.props.nodeIndexes.map((nodeIndex) => [nodeIndex, undefined]);
    }else{
      nodeIndexes = this.props.nodeIndexes;
   }

    const treeNodes = this.getTree(nodeIndexes, '', filterText);

    return (
      <div style={{ padding: '0 10px', margin: '20px 0px' }}>
        <div>
          <div>
            <div style={{ width: '100%', position: 'relative', height: 30 }}>
              <div className={`${styles.code} ${styles.heading}`}>
                Method name
                <input
                  className={styles.filter}
                  type="text"
                  placeholder="Type to filter"
                  autoFocus
                  defaultValue={filterText}
                  onChange={this.debouncedHandleFilterChange}
                />
              </div>
              <div className={`${styles.onStack} ${styles.heading}`}/>
            </div>
            {treeNodes}
          </div>
          {filterText && !treeNodes.length && (
            <p className={styles.alert}>Sorry, no results found for your search query!</p>
          )}
        </div>
      </div>
    );
  }
}

MethodTreeComponent.propTypes = {
  allNodes: React.PropTypes.array,
  nodeIndexes: React.PropTypes.array,
  nextNodesAccessorField: React.PropTypes.string.isRequired,
  methodLookup: React.PropTypes.array,
};

export default withRouter(MethodTreeComponent);
