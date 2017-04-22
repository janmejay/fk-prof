import React, { Component } from 'react';
import StackTreeElement from 'components/StackTreeElementComponent';
import { withRouter } from 'react-router';
import memoize from 'utils/memoize';
import debounce from 'utils/debounce';

import styles from './MethodTreeComponent.css';
import treeStyles from 'components/StackTreeElementComponent/StackTreeElementComponent.css';
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
    this.userToggledNode = null;
    this.renderSubTree = this.renderSubTree.bind(this);
    this.toggle = this.toggle.bind(this);
    this.handleFilterChange = this.handleFilterChange.bind(this);
    this.highlight = this.highlight.bind(this);
    this.debouncedHandleFilterChange = debounce(this.handleFilterChange, 250);
    this.memoizedDedupeNodes = memoize(
      dedupeNodes(props.allNodes), stringifierFunction, true,
    );
  }

  renderSubTree = (nodeIndexes = [], parentPath = '', parentIndent=0, parentHasSiblings=false, autoExpand=false, filterText) => {
    // only need to de-dupe for bottom-up not top-down,
    // hence the ternary :/
    //TODO: Check if this memoization can be skipped with just using the isOpen state variable
    const dedupedNodes = this.props.nextNodesAccessorField === 'parent'
      ? this.memoizedDedupeNodes(nodeIndexes, parentPath)
      : nodeIndexes.map((nodeIndex) => this.props.allNodes[nodeIndex]).slice().sort((a, b) => b.onStack - a.onStack);

    const percentageDenominator = (this.props.allNodes.length > 0)? this.props.allNodes[0].onStack: 1;
    //Indent should always be zero if no parent
    //Otherwise, if parent has siblings or if this node has siblings, do a major indent of the nodes, minor indent otherwise
    const indent = !parentPath ? 0 : ((parentHasSiblings || dedupedNodes.length > 1 )? parentIndent + 10 : parentIndent + 1);
    const hasSiblings = dedupedNodes.length > 1;
    
    const dedupedTreeNodes = dedupedNodes.map((n, i) => {
      let uniqueId, newNodeIndexes, countToDisplay;
      let displayName = this.props.methodLookup[n.name];

      //This condition is equivalent to (n instanceOf HotMethodNode)
      //since nextNodesAccessorField is = parent in hot method view and
      //Node type for dedupedNodes is HotMothodNode from above
      if (this.props.nextNodesAccessorField === 'parent') {
        uniqueId = `${parentPath}->${n.identifier()}`;
        newNodeIndexes = n.parentsWithSampledCallCount;
        const lineNoOrNot = (n.belongsToTopLayer)? '' : ':' + n.lineNo;
        displayName = displayName + lineNoOrNot ;
        countToDisplay = n.sampledCallCount;
      } else {
        // using the index i because in call tree the name of sibling nodes
        // can be same, react will throw up, argh!
        uniqueId = `${i+parentPath}->${n.name}:${n.lineNo}`;
        newNodeIndexes = n.children;
        displayName = displayName + ':' + n.lineNo;
        countToDisplay = n.onStack;
      }
      //By default, keep auto expand behavior of children same as parent.
      //As a special case, if this is the node toggled by user and it was expanded, then enable auto expand in the children of this node
      let childAutoExpand = autoExpand;
      if((this.userToggledNode && this.userToggledNode == uniqueId) && this.state.opened[uniqueId]) {
        childAutoExpand = true;
      }
      //Following condition should always be evaluated after the childAutoExpand is set, since this mutates this.state.opened[uniqueId]
      //If auto expand behavior is enabled and only single node is being rendered, expand the node
      if(autoExpand && dedupedNodes.length == 1) {
        this.state.opened[uniqueId] = true;
      }
      const onStackPercentage = Number((countToDisplay * 100) / percentageDenominator).toFixed(2);
      const showDottedLine = parentPath && dedupedNodes.length >= 2 && dedupedNodes.length !== i + 1 &&
        this.state.opened[uniqueId];
      const isHighlighted = Object.keys(this.state.highlighted)
        .filter(filterPaths.bind(null, uniqueId));

      const nodeRender =
        <StackTreeElement
          data-nodename={displayName}
          key={uniqueId}
          stackline={displayName}
          samples={countToDisplay}
          samplesPct={onStackPercentage}
          indent={indent}
          nodestate={this.state.opened[uniqueId]}
          highlight={isHighlighted.length}
          subdued={dedupedNodes.length == 1 ? true : false}
          onHighlight={this.highlight.bind(this, uniqueId)}
          onClick={newNodeIndexes ? this.toggle.bind(this, uniqueId) : noop}>
        </StackTreeElement>;
      const childRender = this.state.opened[uniqueId] && newNodeIndexes && this.renderSubTree(newNodeIndexes, uniqueId, indent, hasSiblings, childAutoExpand);
      if(childRender) {
        return ([nodeRender, childRender]);
      } else {
        return ([nodeRender]);
      }
    });

    //dedupedTreeNodes is an array of node element and sub-array of its child elements
    //Filter text is only applied on first-level nodes, so just check the first array element below
    return filterText
      ? dedupedTreeNodes.filter(nodeElements => nodeElements.length && nodeElements[0].props['data-nodename'].match(new RegExp(filterText, 'i')))
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
    this.userToggledNode = open;
    this.setState({
      opened: {
        ...this.state.opened,
        [open]: !this.state.opened[open],
      },
    });
  }

  handleFilterChange (e) {
    const { pathname, query } = this.props.location;
    this.props.router.push({ pathname, query: { ...query, [this.props.filterKey]: e.target.value } });
  }

  render () {
    const filterText = this.props.location.query[this.props.filterKey];
    const { nextNodesAccessorField } = this.props;
    let nodeIndexes;
    if (nextNodesAccessorField === 'parent') {
      nodeIndexes = this.props.nodeIndexes.map((nodeIndex) => [nodeIndex, undefined]);
    } else {
      nodeIndexes = this.props.nodeIndexes;
    }
    const treeNodes = this.renderSubTree(nodeIndexes, '', 0, false, false, filterText);

    return (
      <div>
        <div className={treeStyles.container}>
          <table>
            <thead><tr>
              <th className={treeStyles.fixedRightCol1}>Samples</th>
              <th>
                <div className={`mdl-textfield mdl-js-textfield mdl-textfield--floating-label is-dirty is-upgraded ${styles.filterBox}`}>
                  <input
                    className={`mdl-textfield__input`}
                    type="text"
                    defaultValue={filterText}
                    autoFocus
                    onChange={this.debouncedHandleFilterChange}
                    id="method_filter"
                  />
                  <label htmlFor="method_filter" className="mdl-textfield__label">Stack Line Filter</label>
                </div>
              </th>
            </tr></thead>
            <tbody>
              {treeNodes}
            </tbody>
          </table>
        </div>
      {filterText && !treeNodes.length && (
        <p className={styles.alert}>Sorry, no results found for your search query!</p>
      )}
      </div>
    );
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
