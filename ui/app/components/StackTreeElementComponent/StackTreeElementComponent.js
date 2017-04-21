import React, { Component } from 'react';
import styles from './StackTreeElementComponent.css';

class StackTreeElementComponent extends Component {
  constructor (props) {
    super(props);
    this.state = {
    };
  }

  getStyleAndIconForNode() {
    if (this.props.nodestate) {
      return [0, "remove"];
    } else {
      return ["mdl-color-text--accent", "add"];
    }
  }

  getIconForHighlight() {
    return this.props.highlight ? "highlight" : "lightbulb_outline";
  }

  render () {
    let leftPadding = (this.props.indent || 0) + "px";
    return (
      <tr className={this.props.highlight ? styles.highlight : this.props.subdued && styles.subdued}>
        <td className={styles.fixedRightCol1}>
          {this.props.samples ? (
            <div className={`${styles.pill} mdl-color-text--primary`}>
              <div className={styles.number}>{this.props.samples}</div>
              <div className={styles.percentage}>
                <div className={styles.shade} style={{ width: `${this.props.samplesPct}%` }} />
                {this.props.samplesPct}%
              </div>
            </div>
          ) : <div>&nbsp;</div>}
        </td>
        <td>
          <div className={styles.stackline} style={{marginLeft: leftPadding}} title={this.props.stackline}>
            <span className={`material-icons mdl-color-text--primary ${styles.nodeIcon}`} onClick={this.props.onHighlight}>
              {this.getIconForHighlight()}
            </span>
            <span className={styles.stacklineText} onClick={this.props.onClick}>
              <span className={`material-icons ${this.getStyleAndIconForNode()[0]} ${styles.nodeIcon}`}>
                {this.getStyleAndIconForNode()[1]}
              </span>
              <span className={`${this.props.highlight && 'mdl-color-text--primary'}`}>{this.props.stackline}</span>
            </span>
          </div>
        </td>
      </tr>
    );
  }
}

export default StackTreeElementComponent;