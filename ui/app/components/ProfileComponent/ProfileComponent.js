import React from 'react';
import { Link, withRouter } from 'react-router';
import Modal from 'react-modal';
import { Chart } from 'react-google-charts';
import moment from 'moment';
import $ from 'jquery';

import styles from './ProfileComponent.css';

class ProfileComponent extends React.Component {
  constructor (props) {
    super(props);
    this.state = {
      collapse: true,
      collapseCount: 3,
      filterText: '',
      statsOpen: false
    };

    this.toggle = this.toggle.bind(this);
    this.setFilterText = this.setFilterText.bind(this);
    this.openStats = this.openStats.bind(this);
    this.closeStats = this.closeStats.bind(this);
    this.handleTimelineReady = this.handleTimelineReady.bind(this);
    this.handleTimelineSelect = this.handleTimelineSelect.bind(this);

    this.timelineChart = null;
    this.stats = null;
    this.ips = [];
    this.selStatIdx = 0;
    this.timeline = {
      cols: [
        {label: "Status", type: "string"},
        {label: "IP", type: "string"},
        {role: "tooltip", type: "string", p: {'html': true}},
        {label: "Start", type: "datetime"},
        {label: "End", type: "datetime"}
      ],
      rows: null,
      opts: {
        timeline: {
          showBarLabels: false
        },
        tooltip: {isHtml: true}
      },
      events: [{
        eventName: 'select',
        callback: this.handleTimelineSelect
      },{
        eventName: 'ready',
        callback: this.handleTimelineReady
      }]
    };

    this.w_start = new Date(this.props.profile.start);
    this.w_end = new Date(this.w_start.getTime() + (this.props.profile.duration * 1000));
  }

  openStats() {
    this.setState({
      statsOpen: true
    });
  }

  closeStats() {
    this.setState({
      statsOpen: false
    });
  }

  setFilterText (e) {
    this.setState({ filterText: e.target.value });
  }

  toggle () {
    this.setState({ collapse: !this.state.collapse });
  }

  render () {
    console.log("yo", this.props.profile);
    const tracesScore = this.props.tracesScore ? this.props.tracesScore : [];    
    const isCollapsable = tracesScore.length > this.state.collapseCount;
    const isCollapsed = (isCollapsable && this.state.collapse);

    const list = isCollapsed
      ? tracesScore.slice(0, 3) :
        tracesScore.filter(t => t.name.indexOf(this.state.filterText) > -1);
    const selectedTraceName = this.props.params.traceName;
    const selectedProfile = this.props.location.query.profileStart;

    if(this.state.statsOpen) {
      if(!this.stats) {
        this.setupStats();
      }
      this.setupTimeline();
    }

    return (
      <div className={styles['main']}>
        <div className={styles.heading}>
          <h5>{this.props.heading}</h5>
          <div className={styles.profileInfoIcon} title="Stats" onClick={this.openStats}>
            <span className="material-icons">more</span>
          </div>
        </div>

        {isCollapsable && !isCollapsed && (
          <div className="mdl-textfield mdl-js-textfield" style={{padding: "8px 20px 4px 20px"}}>
            <input
              className="mdl-textfield__input"
              type="text"
              value={this.state.filterText}
              onChange={this.setFilterText}
              placeholder="Filter traces"
              style={{fontSize: "14px", padding: "4px"}}
            />
          </div>
        )}
        
        {list && list.length > 0 && (
          <div style={{padding: "4px 16px"}}>
            {list.map(l => (
              <div className={`${styles.itemContainer} ${(l.name === selectedTraceName && this.props.profile.start === selectedProfile) && styles.highlighted}`}
              key={l.name}>
                <Link
                  to={loc => ({ pathname: `/profiler/profile-data/${l.name}`, query: { ...loc.query, profileStart: this.props.profile.start }})}>
                  <span>(</span><span className="mdl-color-text--primary">{l.score}</span><span>)</span>
                  <span>&nbsp;</span>
                  <span>{l.name}</span>
                </Link>
              </div>
            ))}
          </div>
        )}

        {isCollapsable && (
          <div className={styles.center}>
            <button
              className="mdl-button mdl-js-button mdl-button--primary"
              style={{fontSize: "12px"}}
              onClick={this.toggle}
            >
              {this.state.collapse ? 'Show more' : 'Show Less'}
            </button>
          </div>
        )}

        <Modal
          isOpen={this.state.statsOpen}
          onRequestClose={this.closeStats}
          style={modalStyles}
          contentLabel={"Profile Stats for " + this.props.heading}
        >
          <div style={{flex: 'none'}}><h4>{"Profile Stats for " + this.props.heading}</h4></div>
          <Chart
            chartType="Timeline"
            columns={this.timeline.cols}
            rows={this.timeline.rows}
            options={this.timeline.opts}
            graph_id="ProfileTimeline"
            width="800px"
            height="100%"
            chartEvents={this.timeline.events}
            chartPackages={['corechart', 'timeline']}
          />
        
          <div style={{flex: 'auto', backgroundColor: '#f4f4f4'}} className='mdl-grid'>
            <div className='mdl-cell mdl-cell--12-col' style={{fontSize: '20px'}}>
              10.10.10.10
            </div>
            <div className='mdl-grid' style={{fontSize: '20px'}}>
             
            </div>
          </div>
        </Modal>
      </div>
    );
  }

  setupStats() {
    this.stats = this.props.profile.profiles.map(p => {
      let stat = {};
      const p_start = new Date(this.w_start.getTime() + (p.start_offset * 1000));
      const p_end = new Date(p_start.getTime() + (p.duration * 1000));
      const tooltip = '<div style="padding: 4px;"><div style="font-size: 14px;" class="mdl-color-text--primary">' + p.recorder_info.ip + '</div>' 
        + '<div style="font-size: 10px;">' + moment(p_start).format("HH:mm:ss") + ' - ' + moment(p_end).format("HH:mm:ss") + '</div></div>';
      stat.basic = [p.status, p.recorder_info.ip, tooltip, p_start, p_end];
      stat.tcov = p.trace_coverage_map;
      stat.samp = p.sample_count;
      stat.rv = p.recorder_version;
      return stat;
    });
    this.ips = this.stats.map((s,i) => [s.basic[1], i]).sort((a, b) => (a[0] < b[0]) ? -1 : ((a[0] > b[0]) ? 1 : 0));
  }

  setupTimeline() {
    this.timeline.rows = !this.stats ? [] : this.stats.map(s => s.basic);
    let fr = this.timeline.rows[0];
    this.timeline.rows.push(["Completed", "1", fr[2], new Date(fr[3].getTime() + 400*1000), new Date(fr[4].getTime() + 500*1000)]);
    this.timeline.rows.push([fr[0], "2", fr[2], new Date(fr[3].getTime() + 100*1000), new Date(fr[4].getTime() - 50*1000)]);

    this.timeline.opts.hAxis = {
      minValue: this.w_start,
      maxValue: this.w_end,
      format: "HH:mm"
    };
  }

  handleTimelineReady(chart) {
    this.timelineChart = chart;
  }

  handleTimelineSelect(chart) {
    this.selStatIdx = chart.chart.getSelection()[0].row;
    this.renderStatDetail();
  }

  renderStatDetail() {
    console.log("molo", this.selStatIdx);
  }
}

ProfileComponent.propTypes = {
  heading: React.PropTypes.string,
  traces: React.PropTypes.array,
  start: React.PropTypes.string,
  workTypeSummary: React.PropTypes.object,
};

const modalStyles  = {
  overlay: {
    zIndex: 10
  },
  content: {
    top: '50%',
    left: '50%',
    right: 'auto',
    bottom: 'auto',
    marginRight: '-50%',
    transform: 'translate(-50%, -50%)',
    height: '70%',
    display: 'flex',
    flexFlow: 'column',
    overflowY: 'scroll'
  }
};

export default withRouter(ProfileComponent);

