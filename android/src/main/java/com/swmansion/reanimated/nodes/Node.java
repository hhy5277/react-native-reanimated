package com.swmansion.reanimated.nodes;

import android.util.SparseArray;

import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.UiThreadUtil;
import com.swmansion.reanimated.EvaluationContext;
import com.swmansion.reanimated.NodesManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import javax.annotation.Nullable;

public abstract class Node<T> {

  public static final Double ZERO = Double.valueOf(0);
  public static final Double ONE = Double.valueOf(1);

  protected final int mNodeID;
  protected final NodesManager mNodesManager;

  private @Nullable List<Node<?>> mChildren; /* lazy-initialized when a child is added */

  public Node(int nodeID, @Nullable ReadableMap config, NodesManager nodesManager) {
    mNodeID = nodeID;
    mNodesManager = nodesManager;
  }


  protected abstract @Nullable T evaluate(EvaluationContext evaluationContext);

  public final @Nullable T value(EvaluationContext evaluationContext) {
    if (evaluationContext.lastLoopsIDs.indexOfKey(mNodeID) < 0) {
      evaluationContext.lastLoopsIDs.put(mNodeID, (long) -1);
    }
    long lastLoopID = evaluationContext.lastLoopsIDs.get(mNodeID);
    if (lastLoopID < evaluationContext.updateLoopID) {
      evaluationContext.lastLoopsIDs.put(mNodeID, evaluationContext.updateLoopID);
      evaluationContext.memoizedValues.put(mNodeID,  evaluate(evaluationContext));
    }
    return (T) evaluationContext.memoizedValues.get(mNodeID);
  }

  /**
   * This method will never return null. If value is null or of a different type we try to cast and
   * return 0 if we fail to properly cast the value. This is to match iOS behavior where the node
   * would not throw even if the value was not set.
   */
  public final Double doubleValue(EvaluationContext evaluationContext) {
    T value = value(evaluationContext);
    if (value == null) {
      return ZERO;
    } else if (value instanceof Double) {
      return (Double) value;
    } else if (value instanceof Number) {
      return Double.valueOf(((Number) value).doubleValue());
    } else if (value instanceof Boolean) {
      return ((Boolean) value).booleanValue() ? ONE : ZERO;
    }
    throw new IllegalStateException("Value of node " + this + " cannot be cast to a number");
  }

  public void addChild(Node child) {
    if (mChildren == null) {
      mChildren = new ArrayList<>();
    }
    mChildren.add(child);
    dangerouslyRescheduleEvaluate(mNodesManager.mGlobalEvaluationContext); // TODO not sure
  }

  public void removeChild(Node child) {
    if (mChildren != null) {
      mChildren.remove(child);
    }
  }

  protected void markUpdated(EvaluationContext context) {
    UiThreadUtil.assertOnUiThread();
    context.updatedNodes.put(mNodeID, this);
    mNodesManager.postRunUpdatesAfterAnimation();
  }

  protected final void dangerouslyRescheduleEvaluate(EvaluationContext context) {
    context.lastLoopsIDs.put(mNodeID, (long) -1);
    markUpdated(context);
  }

  protected final void forceUpdateMemoizedValue(T value, EvaluationContext context) {
    context.memoizedValues.put(mNodeID, value);
    markUpdated(context);
  }

  private static void findAndUpdateNodes(Node node, Set<Node> visitedNodes, Stack<FinalNode> finalNodes) {
    if (visitedNodes.contains(node)) {
      return;
    } else {
      visitedNodes.add(node);
    }

    List<Node> children = node.mChildren;
    if (children != null) {
      for (Node child : children) {
        findAndUpdateNodes(child, visitedNodes, finalNodes);
      }
    }
    if (node instanceof FinalNode) {
      finalNodes.push((FinalNode) node);
    }
  }

  public static void runUpdates(EvaluationContext evaluationContext) {
    UiThreadUtil.assertOnUiThread();
    SparseArray<Node> updatedNodes = evaluationContext.updatedNodes;
    Stack<FinalNode> finalNodes = new Stack<>();
    for (int i = 0; i < updatedNodes.size(); i++) {
      findAndUpdateNodes(updatedNodes.valueAt(i), new HashSet<Node>(), finalNodes);
    }
    while (!finalNodes.isEmpty()) {
      finalNodes.pop().update();
    }
    updatedNodes.clear();
    evaluationContext.updateLoopID++;
  }
}
