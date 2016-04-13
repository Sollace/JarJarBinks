package com.blazeloader.util.transformers.transformations;

import static org.objectweb.asm.Opcodes.ACC_FINAL;

import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class FinalityTransformation extends Transformation {
	
	private final boolean finalValue;
	
	public FinalityTransformation(TargetSelector selector, String targetClass, boolean setFinalTo) {
		super(selector, targetClass);
		finalValue = setFinalTo;
	}

	@Override
	public void transformField(FieldNode node) {
		int access = setFinality(node.access, finalValue);
		if (access != node.access) {
			System.out.println("FIELD: Change final: " + node.name + " -> " + finalValue);
			node.access = access;
		}
	}

	@Override
	public void transformMethod(MethodNode node) {
		int access = setFinality(node.access, finalValue);
		if (access != node.access) {
			System.out.println("METHOD: Change final: " + node.name + node.signature + " -> " + finalValue);
			node.access = access;
		}
	}
	
    protected final int setFinality(int currAccess, boolean finalValue) {
    	if (finalValue) {
    		currAccess |= ACC_FINAL;
        } else {
        	currAccess &= ~ACC_FINAL;
        }
    	return currAccess;
    }
}
