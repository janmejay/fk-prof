import React, { Component } from 'react';
import Header from 'components/HeaderComponent';

import 'components/RootComponent/RootComponent.css';

const BaseComponent = Component => class extends React.Component {
  componentDidUpdate () {
      componentHandler.upgradeDom(); // eslint-disable-line
  }

  render () {
    return <Component {...this.props} />;
  }
  };

class RootComponent extends Component {
  render () {
    return (
      <div>
        <Header />
        <main className="app mdl-layout__content">
          <div className="page-content">
            {this.props.children}
          </div>
        </main>
      </div>
    );
  }
}

export default BaseComponent(RootComponent);
