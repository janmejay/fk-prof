import React, { PureComponent } from 'react';
import { CellMeasurer, CellMeasurerCache, List, AutoSizer, WindowScroller, Grid } from 'react-virtualized';
import styles from './SampleComponent.css';
import StacklineDetailComponent from 'components/StacklineDetailComponent';

export default class SampleComponent extends PureComponent {

  constructor (props, context) {
    super(props, context)

    this._cache = new CellMeasurerCache({
      defaultWidth: 100,
      minWidth: 100,
      fixedHeight: true
    })

    this._cellRenderer = this._cellRenderer.bind(this)

    this.list = []
    for(let i = 0;i < 2;i++) {
      let chars = Math.ceil(100 + Math.random() * 150);
      let str = "" + i + " ";
      for(let j = 0;j < chars;j++) {
        if ((j + 1) % 10 == 0) {
          str += " ";
        } else {
          str += "a";
        }
      }
      this.list.push(str);
    }

  }

  render () {
    const width = 500

    return (
      <Grid
        className={styles.BodyGrid}
        columnCount={1}
        columnWidth={this._cache.columnWidth}
        deferredMeasurementCache={this._cache}
        height={400}
        overscanColumnCount={0}
        overscanRowCount={2}
        cellRenderer={this._cellRenderer}
        rowCount={this.list.length}
        rowHeight={35}
        width={width}
      />
    )
  }

  _cellRenderer ({ columnIndex, key, parent, rowIndex, style }) {

    const content = this.list[(rowIndex + columnIndex) % this.list.length]
    
    return (
      <CellMeasurer
        cache={this._cache}
        columnIndex={columnIndex}
        key={key}
        parent={parent}
        rowIndex={rowIndex}
      >
        <div
          style={{
            ...style,
            height: 35,
            whiteSpace: 'nowrap'
          }}
        >
          {content}
        </div>
      </CellMeasurer>
    )
  }
}