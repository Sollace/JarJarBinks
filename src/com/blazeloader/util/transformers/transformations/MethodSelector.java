package com.blazeloader.util.transformers.transformations;

import java.util.List;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class MethodSelector implements TargetSelector {
	private Transformation transformation;
	private final String methodDesc;
	private final String methodName;
	private final boolean global;
	
	public MethodSelector(String name) {
		global = "*".equals(name);
		methodDesc = name.split(" ")[1];
		methodName = name.split(" ")[0];
	}
	
	public TargetSelector initWith(Transformation transformation) {
		this.transformation = transformation;
		return this;
	}
	
	public boolean match(ClassNode cls) {
        boolean didApply = false;
        for (MethodNode method : (List<MethodNode>)cls.methods) {
            if (global || (method.name.equals(methodName) && method.desc.equals(methodDesc))) {
            	transformation.transformMethod(method);
                didApply = true;
            }
        }
        return didApply;
    }
}
