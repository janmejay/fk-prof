/**
 * Created by rohit.patiyal on 21/04/17.
 */

export default class HotMethodRenderNode{
  constructor(belongsToTopLayer, lineNo, name, sampledCallCount = 0, parent = []){
    this.belongsToTopLayer = belongsToTopLayer;
    this.lineNo = lineNo;
    this.name = name;
    this.sampledCallCount = sampledCallCount;
    this.parent = parent;
  }

  identifier(){
    if(!this.belongsToTopLayer){
      return `${this.name}:${this.lineNo}`;
    }else{
      return this.name;
    }
  }
}


