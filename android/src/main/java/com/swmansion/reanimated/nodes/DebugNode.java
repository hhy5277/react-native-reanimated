package com.swmansion.reanimated.nodes;

import android.util.Log;

import com.facebook.react.bridge.ReadableMap;
import com.swmansion.reanimated.EvaluationContext;
import com.swmansion.reanimated.NodesManager;

public class DebugNode extends Node {

  private final String mMessage;
  private final int mValueID;

  public DebugNode(int nodeID, ReadableMap config, NodesManager nodesManager) {
    super(nodeID, config, nodesManager);
    mMessage = config.getString("message");
    mValueID = config.getInt("value");
  }

  @Override
  protected Object evaluate(EvaluationContext evaluationContext) {
    Object value = mNodesManager.findNodeById(mValueID, Node.class).value(evaluationContext);
    Log.d("REANIMATED", String.format("%s %s", mMessage, value));
    return value;
  }
}
