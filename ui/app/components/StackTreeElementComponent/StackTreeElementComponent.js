import React, { Component } from 'react';
import styles from './StackTreeElementComponent.css';

class StackTreeElementComponent extends Component {
  constructor (props) {
    super(props);
    this.state = {
    };
  }

  getIconForNode() {
    switch(this.props.nodestate) {
      case true:
        return "expand_less";
      case false:
      default:
        return "expand_more";
    }
  }

  getIconForHighlight() {
    return this.props.highlight ? "highlight" : "lightbulb_outline";
  }

  render () {
    let leftPadding = ((this.props.indent || 0) * 7) + "px";
    return (
      <tr className={this.props.highlight && styles.inverted}>
        <td className={styles.fixedRightCol1}>
          {this.props.onstack ? (
            <div className={`${styles.pill} mdl-color-text--primary`}>
              <div className={styles.number}>{this.props.onstack}</div>
              <div className={styles.percentage}>
                <div className={styles.shade} style={{ width: `${this.props.onstackPct}%` }} />
                {this.props.onstackPct}%
              </div>
            </div>
          ) : <div>&nbsp;</div>}
        </td>
        <td className={styles.fixedRightCol2}>
          {!!this.props.oncpu ? (
            <div className={`${styles.pill} mdl-color-text--primary`}>
              <div className={styles.number}>{this.props.oncpu}</div>
              <div className={styles.percentage}>
                <div className={styles.shade} style={{ width: `${this.props.oncpuPct}%` }} />
                {this.props.oncpuPct}%
              </div>
            </div>
          ) : <div>&nbsp;</div>}
        </td>
        <td>
          <div className={styles.stackline} style={{marginLeft: leftPadding}} title={this.props.stackline}>
            <span className={`material-icons mdl-color-text--primary-dark ${styles.nodeIcon}`} onClick={this.props.onHighlight}>
              {this.getIconForHighlight()}
            </span>
            <span className={styles.stacklineText} onClick={this.props.onClick}>
              <span className={`material-icons mdl-color-text--primary-dark ${styles.nodeIcon}`}>
                {this.getIconForNode()}
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