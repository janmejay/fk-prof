/**
 * Created by rohit.patiyal on 19/04/17.
 */
export default class FrameNode{
  constructor (name, childCount, lineNo, onStack, onCPU){
    this.childCount = childCount;
    this.lineNo = lineNo;
    this.name = name;
    this.onStack = onStack;
    this.onCPU = onCPU;
    this.children = [];
  }
  hasParent(){
    return (this.parent !== undefined);
  }
}
