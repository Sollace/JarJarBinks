package com.blazeloader.util.transformers.transformations;

import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PROTECTED;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;

import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import com.blazeloader.bl.obf.AccessLevel;

public class PublicityTransformation extends Transformation {
	private static final int ACC_PACKAGE = 0x0000;
	
	public final AccessLevel access;
	
	public PublicityTransformation(TargetSelector selector, String targetClass, AccessLevel level) {
		super(selector, targetClass);
		access = level;
	}

	@Override
	public void transformField(FieldNode node) {
		int access = setPublicity(node.access, this.access);
		if (access != node.access) {
			System.out.println("FIELD: Change publicity: " + node.name + " -> " + this.access);
			node.access = access;
		}
	}

	@Override
	public void transformMethod(MethodNode node) {
		int access = setPublicity(node.access, this.access);
		if (access != node.access) {
			System.out.println("METHOD: Change publicity: " + node.name + " " + node.desc + " -> " + this.access);
			node.access = access;
		}
	}
	
	/**
	 * Computes a new publicity level for a method or field.
	 * Only allows changes to higher publicity levels.
	 */
	protected final int setPublicity(int currAccess, AccessLevel publicity) {
        int pubValue = publicity.getValue();
        int ret = (currAccess & ~7);
        switch (currAccess & 7) {
        	case ACC_PUBLIC:
        		if (pubValue == ACC_PROTECTED) return currAccess;
            case ACC_PROTECTED:
            	if (pubValue == ACC_PACKAGE) return currAccess;
            case ACC_PACKAGE:
            	if (pubValue == ACC_PRIVATE) return currAccess;
        }
        return ret | pubValue;
    }
}
