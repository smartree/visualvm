/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * 
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 * 
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * 
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.tools.visualvm.truffle.heapwalker.r;

import com.sun.tools.visualvm.truffle.heapwalker.TruffleFrame;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.SortOrder;
import org.netbeans.lib.profiler.heap.FieldValue;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.ObjectFieldValue;
import org.netbeans.lib.profiler.heap.PrimitiveArrayInstance;
import org.netbeans.modules.profiler.heapwalker.v2.java.InstanceReferenceNode;
import org.netbeans.modules.profiler.heapwalker.v2.java.PrimitiveNode;
import org.netbeans.modules.profiler.heapwalker.v2.model.DataType;
import org.netbeans.modules.profiler.heapwalker.v2.model.HeapWalkerNode;
import org.netbeans.modules.profiler.heapwalker.v2.model.HeapWalkerNodeFilter;
import org.netbeans.modules.profiler.heapwalker.v2.ui.UIThresholds;
import org.netbeans.modules.profiler.heapwalker.v2.utils.NodesComputer;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Jiri Sedlacek
 */
@ServiceProvider(service = HeapWalkerNode.Provider.class, position = 200)
public class RItemsProvider extends HeapWalkerNode.Provider {

    public String getName() {
        return "items";
    }

    public boolean supportsView(Heap heap, String viewID) {
        return viewID.startsWith("r_");
    }

    public boolean supportsNode(HeapWalkerNode parent, Heap heap, String viewID) {
        if (parent instanceof RObjectNode && !(parent instanceof RObjectReferenceNode)) {
            RObject robject = HeapWalkerNode.getValue(parent, RObject.DATA_TYPE, heap);
            if (robject != null) {
                if (robject.getFieldValues().isEmpty()) {
                    TruffleFrame frame = robject.getFrame();
                    if (frame != null) {
                        return frame.isTruffleFrame();
                    } else {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    public HeapWalkerNode[] getNodes(HeapWalkerNode parent, Heap heap, String viewID, HeapWalkerNodeFilter viewFilter, List<DataType> dataTypes, List<SortOrder> sortOrders) {
        return getNodes(getFields(parent, heap), parent, heap, viewID, viewFilter, dataTypes, sortOrders);
    }

    static HeapWalkerNode[] getNodes(List<FieldValue> fields, HeapWalkerNode parent, Heap heap, String viewID, HeapWalkerNodeFilter viewFilter, List<DataType> dataTypes, List<SortOrder> sortOrders) {
        if (fields == null) return null;

        NodesComputer<Integer> computer = new NodesComputer<Integer>(fields.size(), UIThresholds.MAX_INSTANCE_FIELDS) {
            protected boolean sorts(DataType dataType) {
                return !DataType.COUNT.equals(dataType);
            }
            protected HeapWalkerNode createNode(Integer index) {
                return RItemsProvider.createNode(fields.get(index), heap);
            }
            protected Iterator<Integer> objectsIterator(int index) {
                return integerIterator(index, fields.size());
            }
            protected String getMoreNodesString(String moreNodesCount)  {
                return "<another " + moreNodesCount + " items left>";
            }
            protected String getSamplesContainerString(String objectsCount)  {
                return "<sample " + objectsCount + " items>";
            }
            protected String getNodesContainerString(String firstNodeIdx, String lastNodeIdx)  {
                return "<items " + firstNodeIdx + "-" + lastNodeIdx + ">";
            }
        };

        return computer.computeNodes(parent, heap, viewID, null, dataTypes, sortOrders);
    }

    
    protected List<FieldValue> getFields(HeapWalkerNode parent, Heap heap) {
        RObject robject = parent == null ? null : HeapWalkerNode.getValue(parent, RObject.DATA_TYPE, heap);
        if (robject == null) return null;

        List<FieldValue> fields = new ArrayList(robject.getFieldValues());
        if (fields.isEmpty()) fields.addAll(robject.getFrame().getLocalFieldValues());
        
        Iterator<FieldValue> fieldsIt = fields.iterator();
        while (fieldsIt.hasNext())
            if (!displayField(fieldsIt.next()))
                fieldsIt.remove();

        return fields;
    }
    
    private boolean displayField(FieldValue field) {
        // display primitive fields
        if (!(field instanceof ObjectFieldValue)) return true;
        
        Instance instance = ((ObjectFieldValue)field).getInstance();
        
        // display null fields
        if (instance == null) return true;
        
        // display DynamicObject fields
        if (RObject.isRObject(instance)) return true;
        
        // display primitive arrays
        if (instance instanceof PrimitiveArrayInstance) return true;
        
        String className = instance.getJavaClass().getName();
        
        // display java.lang.** and com.oracle.truffle.r.runtime.data.** fields
        if (className.startsWith("java.lang.") ||
            className.startsWith("com.oracle.truffle.r.runtime.data."))
            return true;
        
        return false;
    }
    
    private static HeapWalkerNode createNode(FieldValue field, Heap heap) {
        if (field instanceof ObjectFieldValue) {
            Instance instance = ((ObjectFieldValue)field).getInstance();
            if (RObject.isRObject(instance)) {
                return new RObjectFieldNode(new RObject(instance), field);
            } else {
                return new InstanceReferenceNode.Field((ObjectFieldValue)field, false);
            }
        } else {
            return new PrimitiveNode.Field(field);
        }
    }

}
