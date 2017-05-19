import React from 'react';
import ReactDOM from 'react-dom';
import {
  Route,
  Router,
  IndexRedirect,
  browserHistory,
} from 'react-router';
import { Provider } from 'react-redux';

import 'react-select/dist/react-select.css';
import 'react-datetime/css/react-datetime.css';
import 'material-design-lite/material.min';
import 'material-design-lite/material.css';

import store from './store';

import Root from 'components/RootComponent';
import App from 'components/AppComponent';
import CPUSampling from 'components/CPUSamplingComponent';
import AggregatedProfileDataContainer from 'components/AggregatedProfileDataContainer';

import './assets/styles/global.css';

window.fetchprofile = function() {
  fetch("http://localhost:3001/api/profile/fk-prof/nfr_cluster_fk-prof-nfr-wheezy1-475145/load-gen-app/cpu-sampling/json-ser-de-ctx_0%20%3E%20hashmap-create-ctx?start=2017-05-06T09%3A30%3A56.638Z")
    .then(a => {console.log("yo", a); window.pr = a;})
    .catch(e => console.log("lo", e));
};

window.parseprofile = function() {
  window.pr.json().then(a => console.log(a)).catch(e => console.error(e));
}

const routes = (
  <Route path="/" component={Root}>
    <IndexRedirect to="/profiler" />
    <Route path="/profiler" component={App}>
      <Route path="/profiler/profile-data/:traceName" component={AggregatedProfileDataContainer} />
    </Route>
    <Route path="/work-type/cpu_sample_work/:traceName" component={CPUSampling} />
  </Route>
);

ReactDOM.render(
  <Provider store={store}>
    <Router history={browserHistory}>
      {routes}
    </Router>
  </Provider>,
  document.getElementById('root'),
);
