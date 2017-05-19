import React, { Component } from 'react';
import styles from './StackTreeElementComponent.css';

class StackTreeElementComponent extends Component {
  constructor (props) {
    super(props);
    this.state = {
    };
  }
  
  shouldComponentUpdate(nextProps, nextState) {
    if(this.props.stackline === "java.net.SocketOutputStream.socketWrite0") {
      console.log("scu", this.props.stackline, nextProps.stackline);
    }
    if(this.props.nodestate !== nextProps.nodestate) {
      if(this.props.stackline === "java.net.SocketOutputStream.socketWrite0") {
        console.log("scu", true);
      }
      return true;
    }
    if(this.props.highlight !== nextProps.highlight) {
      if(this.props.stackline === "java.net.SocketOutputStream.socketWrite0") {
        console.log("scu", true);
      }
      return true;
    }
    if(this.props.stackline === "java.net.SocketOutputStream.socketWrite0") {
      console.log("scu", false);
    }
    return false;
  }

  getStyleAndIconForNode() {
    if (this.props.nodestate) {
      return [styles.collapsedIcon, "play_arrow"];
    } else {
      return ["mdl-color-text--accent", "play_arrow"];
    }
  }

  getIconForHighlight() {
    return this.props.highlight ? "highlight" : "lightbulb_outline";
  }

  render () {
    if(this.props.stackline === "java.net.SocketOutputStream.socketWrite0") {
      console.log("Sdfsf");
    }
    let leftPadding = (this.props.indent || 0) + "px";
    return (
      <div className={this.props.highlight ? styles.highlight : this.props.subdued && styles.subdued}>
        <div>
          <div className={styles.stackline} style={{marginLeft: leftPadding}} title={this.props.nodename}>
            <span className={`material-icons mdl-color-text--primary ${styles.nodeIcon}`} onClick={this.props.onHighlight}>
              {this.getIconForHighlight()}
            </span>
            <span className={styles.stacklineText} onClick={this.props.onClick}>
              <span className={`material-icons ${this.getStyleAndIconForNode()[0]} ${styles.nodeIcon}`}>
                {this.getStyleAndIconForNode()[1]}
              </span>
              <span>{this.props.stackline}</span>
            </span>
          </div>
        </div>
      </div>
    );
  }
}

export default StackTreeElementComponent;



      /*<tr className={this.props.highlight ? styles.highlight : this.props.subdued && styles.subdued}>
        <td>
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
          <div className={styles.stackline} style={{marginLeft: leftPadding}} title={this.props.nodename}>
            <span className={`material-icons mdl-color-text--primary ${styles.nodeIcon}`} onClick={this.props.onHighlight}>
              {this.getIconForHighlight()}
            </span>
            <span className={styles.stacklineText} onClick={this.props.onClick}>
              <span className={`material-icons ${this.getStyleAndIconForNode()[0]} ${styles.nodeIcon}`}>
                {this.getStyleAndIconForNode()[1]}
              </span>
              <span>{this.props.stackline}</span>
            </span>
          </div>
        </td>
      </tr>*/