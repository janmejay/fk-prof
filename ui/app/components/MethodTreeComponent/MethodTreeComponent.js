import React, { Component } from 'react';
import StackTreeElement from 'components/StackTreeElementComponent';
import { withRouter } from 'react-router';
import memoize from 'utils/memoize';
import debounce from 'utils/debounce';

import styles from './MethodTreeComponent.css';
import treeStyles from 'components/StackTreeElementComponent/StackTreeElementComponent.css';

const noop = () => {};
const filterPaths = (pathSubset, k) => k.indexOf(pathSubset) === 0;

const getNode = allNodes => node => Number.isInteger(node) ? allNodes[node] : node;

const dedupeNodes = allNodes => nextNodesAccessorField => (nodes) => {
  let globalOnCPUSum = 0;
  const dedupedNodes = nodes.reduce((prev, curr) => {
    let childOnStack;
    let newPrev = Object.assign({}, prev);
    let newCurr = Object.assign({}, curr);
    if (Array.isArray(curr)) {
      childOnStack = curr[1];
      newCurr = Object.assign({}, allNodes[curr[0]]);
    } else if (Number.isInteger(curr)) {
      newCurr = Object.assign({}, allNodes[curr]);
    } else {
      // will be executed only for top level nodes
      // store the global onCPU count
      globalOnCPUSum += curr.onCPU;
      newCurr.onStack = curr.onCPU;
    }
    const evaluatedOnStack = childOnStack || newCurr.onStack;
    newCurr.onStack = evaluatedOnStack;
    // only do this if it's bottom-up or nextNodesAccessorField === 'parent'
    // change structure of parent array, store onStack also
    if (nextNodesAccessorField === 'parent') {
      newCurr[nextNodesAccessorField] = newCurr.name
        ? [[...newCurr[nextNodesAccessorField], evaluatedOnStack]] : [];
    } else {
      // children case, as children [] might not be present
      newCurr[nextNodesAccessorField] = newCurr[nextNodesAccessorField] || [];
    }
    // use child's onStack value if available,
    // will be available from penultimate node level

    if (!newPrev[newCurr.name]) {
      newPrev[newCurr.name] = newCurr;
    } else {
      newPrev[newCurr.name].onStack += evaluatedOnStack;
      newPrev[newCurr.name].onCPU += newCurr.onCPU;
      newPrev[newCurr.name][nextNodesAccessorField] = [
        ...newPrev[newCurr.name][nextNodesAccessorField],
        ...newCurr[nextNodesAccessorField],
      ];
    }
    return newPrev;
  }, {});
  return {
    dedupedNodes: Object.keys(dedupedNodes)
      .map(k => ({ ...dedupedNodes[k] }))
      .sort((a, b) => b.onStack - a.onStack),
    globalOnCPUSum,
  };
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
      dedupeNodes(props.allNodes)(props.nextNodesAccessorField), stringifierFunction, true,
    );
  }

  renderSubTree = percentageDenominator => (nodes = [], pName = '', indent=0, autoExpand=false, filterText) => {
    // only need to de-dupe for bottom-up not top-down,
    // hence the ternary :/
    const { dedupedNodes, globalOnCPUSum } = this.props.nextNodesAccessorField === 'parent'
      ? this.memoizedDedupeNodes(...nodes)
      : { dedupedNodes: nodes.map(getNode(this.props.allNodes)).slice().sort((a, b) => b.onStack - a.onStack) };
    
    // for call tree, it'll be passed from the parent
    // and for bottom-up we'll use the top level globalOnCPUSum,
    // and pass it around till the leaf level
    percentageDenominator = percentageDenominator || globalOnCPUSum;
    const dedupedTreeNodes = dedupedNodes.map((n, i) => {
      // using the index because in call tree the name of sibling nodes
      // can be same, react will throw up, argh!
      const uniqueId = `${this.props.nextNodesAccessorField !== 'parent' ? i : ''}${pName.toString()}->${n.name.toString()}`;
      const newNodes = n[this.props.nextNodesAccessorField];
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
      const displayName = this.props.methodLookup[n.name];
      const onStackPercentage = Number((n.onStack * 100) / percentageDenominator).toFixed(2);
      const onCPUPercentage = Number((n.onCPU * 100) / percentageDenominator).toFixed(2);
      const showDottedLine = pName && dedupedNodes.length >= 2 && dedupedNodes.length !== i + 1 &&
        this.state.opened[uniqueId];
      const isHighlighted = Object.keys(this.state.highlighted)
        .filter(filterPaths.bind(null, uniqueId));
      const nodeRender =
      <StackTreeElement
        data-nodename={displayName}
        key={uniqueId}
        stackline={displayName}
        oncpu={n.onCPU}
        oncpuPct={onCPUPercentage}
        onstack={(this.props.nextNodesAccessorField !== 'parent' || pName) ? n.onStack : null}
        onstackPct={(this.props.nextNodesAccessorField !== 'parent' || pName) ? onStackPercentage : null}
        indent={indent}
        nodestate={this.state.opened[uniqueId]}
        highlight={isHighlighted.length}
        onHighlight={this.highlight.bind(this, uniqueId)}
        onClick={newNodes ? this.toggle.bind(this, uniqueId) : noop}>
      </StackTreeElement>;
      const childRender = this.state.opened[uniqueId] && newNodes && this.renderSubTree(percentageDenominator)(newNodes, uniqueId, indent+1, childAutoExpand);
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
    const treeNodes = this.renderSubTree(this.props.percentageDenominator)(this.props.nodes, '', 0, false, filterText);
    return (
      <div>
        <div className={treeStyles.container}>
          <table>
            <thead><tr>
              <th className={treeStyles.fixedRightCol1}>On Stack</th>
              <th className={treeStyles.fixedRightCol2}>On CPU</th>
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
                  <label htmlFor="method_filter" className="mdl-textfield__label">Stack Line</label>
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
  nodes: React.PropTypes.array,
  nextNodesAccessorField: React.PropTypes.string.isRequired,
  methodLookup: React.PropTypes.array,
  filterKey: React.PropTypes.string
};

export default withRouter(MethodTreeComponent);
