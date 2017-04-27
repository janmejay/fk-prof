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
    this.state = {
      opened: {}, // keeps track of all opened/closed nodes
      highlighted: {}, // keeps track of all highlighted nodes
    };
    this.userToggledNode = null;
    this.renderTree = this.renderTree.bind(this);
    this.toggle = this.toggle.bind(this);
    this.handleFilterChange = this.handleFilterChange.bind(this);
    this.highlight = this.highlight.bind(this);
    this.debouncedHandleFilterChange = debounce(this.handleFilterChange, 250);
    this.memoizedDedupeNodes = memoize(
      dedupeNodes(props.allNodes), stringifierFunction, true);
  }

  renderTree = (nodeIndexes = [], filterText) => {
    const renderStack = [];
    renderStack.push({
      ae: false, //autoExpand behaviour
      p_pth: '', //parent path
      gen: {
        nis: nodeIndexes, //indexes of first-level nodes in the tree subject to de-duplication
        p_ind: 0, //indentation of parent node
        p_sib: false //parentHasSiblings
      },
      node: null
    });

    const percentageDenominator = (this.props.allNodes.length > 0)? this.props.allNodes[0].onStack: 1;
    const dedupedTreeNodes = [];

    while(renderStack.length > 0) {
      let se = renderStack.pop();
      if (se.gen) {
        // only need to de-dupe for bottom-up not top-down,
        // hence the ternary :/
        //TODO: Check if this memoization can be skipped with just using the isOpen state variable
        const dedupedNodes = this.props.nextNodesAccessorField === 'parent'
          ? this.memoizedDedupeNodes(...se.gen.nis)
          : se.gen.nis.map((nodeIndex) => this.props.allNodes[nodeIndex]).slice().sort((a, b) => b.onStack - a.onStack);

        //Indent should always be zero if no parent
        //Otherwise, if parent has siblings or if this node has siblings, do a major indent of the nodes, minor indent otherwise
        const indent = !se.p_pth ? 0 : ((se.gen.p_sib || dedupedNodes.length > 1 ) ? se.gen.p_ind + 10 : se.gen.p_ind + 3);
        renderStack.push({
          ae: se.ae,
          p_pth: se.p_pth,
          gen: null,
          node: {
            dn: dedupedNodes, //first-level nodes
            ind: indent, //indentation to be applied to rendered node
            idx: 0 //index in array "dn" to identify the node to render
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

        let uniqueId, newNodeIndexes, countToDisplay;
        let displayName = this.props.methodLookup[n.name];
        //If this is a first-level node(p_pth will be empty) and filter is applied, skip rendering of node if display name does not match the filter
        if(filterText && !se.p_pth && !displayName.match(new RegExp(filterText, 'i'))) {
          continue;
        }

        //This condition is equivalent to (n instanceOf HotMethodNode)
        //since nextNodesAccessorField is = parent in hot method view and
        //Node type for dedupedNodes is HotMethodNode from above
        if (this.props.nextNodesAccessorField === 'parent') {
          uniqueId = `${se.p_pth}->${n.identifier()}`;
          newNodeIndexes = n.parentsWithSampledCallCount;
          const lineNoOrNot = (n.belongsToTopLayer)? '' : ':' + n.lineNo;
          displayName = displayName + lineNoOrNot;
          countToDisplay = n.sampledCallCount;
        } else {
          // using the index i because in call tree the name of sibling nodes
          // can be same, react will throw up, argh!
          uniqueId = `${se.p_pth}->${n.name}:${n.lineNo}`;
          newNodeIndexes = n.children;
          displayName = displayName + ':' + n.lineNo;
          countToDisplay = n.onStack;
        }

        //By default, keep auto expand behavior of children same as parent.
        //As a special case, if this is the node toggled by user and it was expanded, then enable auto expand in the children of this node
        let childAutoExpand = se.ae;
        if((this.userToggledNode && this.userToggledNode == uniqueId) && this.state.opened[uniqueId]) {
          childAutoExpand = true;
        }
        // Following condition should always be evaluated after the childAutoExpand is set, since this mutates this.state.opened[uniqueId]
        // If auto expand behavior is enabled and only single node is being rendered, expand the node
        // Or if the node has no children, then expand the node, so that expanded icon is rendered against this node
        if((se.ae && se.node.dn.length == 1) || newNodeIndexes.length == 0) {
          this.state.opened[uniqueId] = true;
        }

        const onStackPercentage = Number((countToDisplay * 100) / percentageDenominator).toFixed(2);
        const isHighlighted = Object.keys(this.state.highlighted)
          .filter(filterPaths.bind(null, uniqueId));
        const nodeRender =
          <StackTreeElement
            data-nodename={displayName}
            key={uniqueId}
            stackline={displayName}
            samples={countToDisplay}
            samplesPct={onStackPercentage}
            indent={se.node.ind}
            nodestate={this.state.opened[uniqueId]}
            highlight={isHighlighted.length}
            subdued={se.node.dn.length == 1 ? true : false}
            onHighlight={this.highlight.bind(this, uniqueId)}
            onClick={newNodeIndexes ? this.toggle.bind(this, uniqueId) : noop}>
          </StackTreeElement>;
        dedupedTreeNodes.push(nodeRender);

        if(this.state.opened[uniqueId] && newNodeIndexes) {
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
    return dedupedTreeNodes;
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
    const treeNodes = this.renderTree(nodeIndexes, filterText);

    return (
      <div>
        <div className={treeStyles.container}>
          <table>
            <thead><tr>
              <th className={treeStyles.fixedRightCol1}>Samples</th>
              <th>
                <div className={`mdl-textfield mdl-js-textfield ${styles.filterBox}`}>
                  <label htmlFor="method_filter" style={{fontWeight: "bold"}}>
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
