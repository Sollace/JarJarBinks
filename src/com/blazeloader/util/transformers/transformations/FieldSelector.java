package com.blazeloader.util.transformers.transformations;

import java.util.List;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

public class FieldSelector implements TargetSelector {
	private Transformation transformation;
	
	private final String fieldName;
	private final boolean global;
	
	public FieldSelector(String name) {
		global = "*".equals(name);
		fieldName = name;
	}
	
	public TargetSelector initWith(Transformation transformation) {
		this.transformation = transformation;
		return this;
	}
	
	public boolean match(ClassNode cls) {
        boolean didApply = false;
        for (FieldNode field : (List<FieldNode>)cls.fields) {
            if (global || field.name.equals(fieldName)) {
            	transformation.transformField(field);
                didApply = true;
            }
        }
        return didApply;
    }
}
