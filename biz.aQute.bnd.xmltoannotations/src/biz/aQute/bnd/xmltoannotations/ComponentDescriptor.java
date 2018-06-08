package biz.aQute.bnd.xmltoannotations;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAttribute;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.component.annotations.ReferenceScope;
import org.osgi.service.component.annotations.ServiceScope;

import aQute.bnd.osgi.Descriptors;
import aQute.bnd.osgi.Verifier;
import aQute.bnd.util.dto.DTO;
import aQute.lib.annotations.setter.AnnotationSetter;
import aQute.lib.strings.Strings;
import aQute.service.reporter.Reporter;

public class ComponentDescriptor extends DTO {

	public static class DS extends DTO {
		public List<ComponentDescriptor> component = new ArrayList<>();
	}

	public static class ComponentAnnotations {
		public String					fqClassName;
		public Component				component;
		public Map<String, Annotation>	methods	= new HashMap<>();
		public Map<String, Annotation>	fields	= new HashMap<>();
		public String					simpleClassName;
	}

	public static class Implementation extends DTO {
		@XmlAttribute(name = "class")
		public String class_;
	}

	public static class Provide extends DTO {
		@XmlAttribute(name = "interface")
		public String interface_;
	}

	public static class Service extends DTO {
		public ServiceScope		scope;
		public List<Provide>	provide	= new ArrayList<>();
	}

	public static class ReferenceDescriptor extends DTO {
		public String					name;
		@XmlAttribute(name = "interface")
		public String					interface_;
		public String					cardinality;
		public ReferencePolicy			policy;

		@XmlAttribute(name = "policy-option")
		public ReferencePolicyOption	policy_option;
		public String					target;
		public String					bind;
		public String					unbind;
		public String					updated;
		public ReferenceScope			scope;
		public String					field;
		@XmlAttribute(name = "field-option")
		public FieldOption				field_option;
		// @XmlAttribute(name="field-collection-type")
		// public FieldCollectionType field_collection_type;

	}

	public static class Property extends DTO {
		public String	name;
		public String	value;
		public String	type;
		public String	_content;	// if the property's content is used
	}

	public static class Properties extends DTO {
		public String entry;
	}

	public Implementation				implementation;
	public boolean						enabled		= true;
	public String						name;
	public String						factory;
	public boolean						immediate	= false;
	public String						activate;
	public String						deactivate;
	public String						modified;
	@XmlAttribute(name = "configuration-policy")
	public ConfigurationPolicy			configuration_policy;
	@XmlAttribute(name = "configuration-pid")
	public String						configuration_pid;
	public List<Properties>				properties	= new ArrayList<>();
	public List<Property>				property	= new ArrayList<>();
	public Service						service;
	public List<ReferenceDescriptor>	reference	= new ArrayList<>();

	/**
	 * Convert this descriptor to annotations on the type, field and methods.
	 */
	public ComponentAnnotations toAnnotations() {
		ComponentAnnotations result = new ComponentAnnotations();

		result.fqClassName = this.implementation.class_;
		result.simpleClassName = Descriptors.getShortName(result.fqClassName);

		AnnotationSetter<Component> c = new AnnotationSetter<>(Component.class);
		result.component = c.a();

		if (this.configuration_pid != null && !this.configuration_pid.isEmpty())
			c.set(c.a()
				.configurationPid(), getConfigurationPids(this.configuration_pid));
		c.set(c.a()
			.enabled(), this.enabled);
		c.set(c.a()
			.immediate(), this.immediate);
		c.set(c.a()
			.configurationPolicy(), this.configuration_policy);
		c.set(c.a()
			.factory(), this.factory);
		c.set(c.a()
			.name(), this.name);
		c.set(c.a()
			.properties(), getPropertiesAsStrings(this.properties));
		c.set(c.a()
			.property(), getPropertyAsStrings(this.property));

		if (this.service != null) {
			String[] types = getProvideAsStrings(this.service.provide);
			c.set(c.a()
				.service(), types);
		}

		if (this.activate != null)
			result.methods.put(this.activate, new AnnotationSetter<>(Activate.class).a());

		if (this.deactivate != null)
			result.methods.put(this.deactivate, new AnnotationSetter<>(Deactivate.class).a());

		if (this.modified != null)
			result.methods.put(this.modified, new AnnotationSetter<>(Modified.class).a());

		for (ReferenceDescriptor reference : this.reference) {
			AnnotationSetter<Reference> r = new AnnotationSetter<>(Reference.class);

			r.set(r.a()
				.target(), reference.target);
			r.set(r.a()
				.cardinality(), toCardinality(reference.cardinality));
			r.set(r.a()
				.policy(), reference.policy);
			r.set(r.a()
				.policyOption(), reference.policy_option);
			r.set(r.a()
				.scope(), reference.scope);

			String fqService = reference.interface_;
			r.set(r.a()
				.service(), fqService + ".class");

			if (reference.field != null) {

				String defaultName = reference.field;
				if (!defaultName.equals(reference.name)) {
					r.set(r.a()
						.name(), reference.name);
				}

				r.set(r.a()
					.bind(), reference.bind);
				r.set(r.a()
					.updated(), reference.updated);
				r.set(r.a()
					.unbind(), reference.unbind);

				result.fields.put(reference.field, r.a());

			} else if (reference.bind != null) {

				String defaultName = stripMethodPrefixes(reference.bind);
				if (!defaultName.equals(reference.name)) {
					r.set(r.a()
						.name(), reference.name);
				}
				r.set(r.a()
					.updated(), reference.updated);
				r.set(r.a()
					.unbind(), reference.unbind);

				result.methods.put(reference.bind, r.a());
			}
		}
		return result;
	}

	private String stripMethodPrefixes(String methodName) {
		if (methodName.startsWith("bind"))
			return methodName.substring(4);

		return methodName;
	}

	/**
	 * <enumeration value="0..1" /> <enumeration value="0..n" />
	 * <enumeration value="1..1" /> <enumeration value="1..n" />
	 * 
	 * @param cardinality
	 * @return
	 */
	private ReferenceCardinality toCardinality(String cardinality) {
		if (cardinality == null) {
			return ReferenceCardinality.MANDATORY;
		}

		switch (cardinality.trim()) {
			case "0..1" :
				return ReferenceCardinality.OPTIONAL;

			case "1..1" :
				return ReferenceCardinality.MANDATORY;
			case "0..n" :
				return ReferenceCardinality.MULTIPLE;
			case "1..n" :
				return ReferenceCardinality.AT_LEAST_ONE;

			default :
				return ReferenceCardinality.MANDATORY;
		}
	}

	private String[] getProvideAsStrings(List<Provide> list) {
		if (list == null)
			return null;

		return list.stream()
			.map(p -> p.interface_ + ".class")
			.toArray(String[]::new);
	}

	private String[] getPropertiesAsStrings(List<Properties> list) {
		if (list == null || list.isEmpty())
			return null;

		return list.stream()
			.map(properties -> properties.entry)
			.toArray(String[]::new);
	}

	private String[] getPropertyAsStrings(List<Property> list) {
		if (list == null || list.isEmpty())
			return null;
		List<String> propDefs = new ArrayList<>();

		list.forEach(property -> {

			if (property.value == null) {
				if (property._content != null) {
					String values[] = property._content.split("[\n\r]+");
					for (String v : values) {
						String propDef = toString(property.name, v, property.type);
						propDefs.add(propDef);
					}
				}
			} else {
				String propDef = toString(property.name, property.value, property.type);
				propDefs.add(propDef);
			}
		});
		return propDefs.toArray(new String[0]);
	}

	private String toString(String name, String value, String type) {
		StringBuilder sb = new StringBuilder();
		sb.append(name);
		if (type != null && !type.equals("String")) {
			sb.append(":")
				.append(type);
		}
		sb.append("=");
		sb.append(value);
		return sb.toString();
	}

	private String[] getConfigurationPids(String configuration_pid) {
		return Strings.split(configuration_pid)
			.toArray(new String[0]);
	}

	public void validate(Reporter reporter) {
		if (implementation == null) {
			reporter.error("No implementation class element specificied");
		} else {
			if (implementation.class_ == null)
				reporter.error("No implementation class attribute specificied");
			else {
				if (!Verifier.isFQN(implementation.class_)) {
					reporter.error("Implementation class not a FQN %s", implementation.class_);
				}
			}
		}
	}

}
