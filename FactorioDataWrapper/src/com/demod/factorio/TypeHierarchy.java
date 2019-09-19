package com.demod.factorio;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

public class TypeHierarchy {
	private final Map<String, String> parents = new HashMap<>();
	private final Set<String> roots = new LinkedHashSet<>();

	public TypeHierarchy(JSONObject json) {
		Utils.<Object>forEach(json, (t, p) -> {
			if (p instanceof String) {
				parents.put(t, (String) p);
			} else {
				roots.add(t);
			}
		});
	}

	public Map<String, String> getParents() {
		return parents;
	}

	public Set<String> getRoots() {
		return roots;
	}

	public boolean isAssignable(String type, String subType) {
		String checkType = subType;
		while (checkType != null) {
			if (type.equals(checkType)) {
				return true;
			}
			checkType = parents.get(checkType);
		}
		return false;
	}
}
