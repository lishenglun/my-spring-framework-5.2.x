/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.method.annotation;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionAttributeStore;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.context.request.WebRequest;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages controller-specific session attributes declared via
 * {@link SessionAttributes @SessionAttributes}. Actual storage is
 * delegated to a {@link SessionAttributeStore} instance.
 *
 * <p>When a controller annotated with {@code @SessionAttributes} adds
 * attributes to its model, those attributes are checked against names and
 * types specified via {@code @SessionAttributes}. Matching model attributes
 * are saved in the HTTP session and remain there until the controller calls
 * {@link SessionStatus#setComplete()}.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class SessionAttributesHandler {

	// 存储，当前Controller中的所有的@SessionAttributes的names属性值
	private final Set<String> attributeNames = new HashSet<>();

	// 存储，当前Controller中的所有的@SessionAttributes的types属性值
	private final Set<Class<?>> attributeTypes = new HashSet<>();

	// 存储所有的已知的，可以被当前处理器处理的属性名
	// （1）可以在构造方法中将当前Controller中的所有的@SessionAttributes的names属性值设置进去；
	// （2）还可以在调用isHandlerSessionAttribute()会添加
	private final Set<String> knownAttributeNames/* 已知属性名称 */ = Collections.newSetFromMap(new ConcurrentHashMap<>(4));

	/**
	 * SessionAttributesHandler主要是对@SessionAttributes的处理工作，而在处理的时候，涉及到SessionAttributes的存储工作，
	 * 在进行存储的时候，需要用到SessionAttributeStore
	 */
	// 具体的参数存储管理类
	private final SessionAttributeStore sessionAttributeStore;


	/**
	 * Create a new session attributes handler. Session attribute names and types
	 * are extracted from the {@code @SessionAttributes} annotation, if present,
	 * on the given type.
	 * @param handlerType the controller type
	 * @param sessionAttributeStore used for session access
	 */
	public SessionAttributesHandler(Class<?> handlerType/* HandlerMethod */, SessionAttributeStore sessionAttributeStore) {
		Assert.notNull(sessionAttributeStore, "SessionAttributeStore may not be null");
		// ⚠️
		this.sessionAttributeStore = sessionAttributeStore;

		/* 1、解析和存储当前Controller上的所有@SessionAttributes的属性值 */

		// 1、查找当前Controller上的，所有的@SessionAttributes注解，并合并为一个@SessionAttributes，然后解析和存储里面的属性值
		SessionAttributes ann = AnnotatedElementUtils.findMergedAnnotation/* 查找注解，并合并 */(handlerType, SessionAttributes.class);
		if (ann != null) {
			// 将当前Controller中的所有的@SessionAttributes的names属性，放入attributeNames中
			Collections.addAll(this.attributeNames, ann.names());
			// 将当前Controller中的所有的@SessionAttributes的names属性，放入attributeTypes中
			Collections.addAll(this.attributeTypes, ann.types());
		}
		// 将attributeNames中的所有值，放入到knownAttributeNames中
		this.knownAttributeNames.addAll(this.attributeNames);
	}


	/**
	 * 判断，当前Controller上，是否标注了@SessionAttributes
	 *
	 * Whether the controller represented by this instance has declared any
	 * session attributes through an {@link SessionAttributes} annotation.
	 *
	 * 此实例表示的控制器是否通过 {@link SessionAttributes} 注解，声明了任意的SessionAttributes。
	 */
	public boolean hasSessionAttributes() {
		return (!this.attributeNames.isEmpty() || !this.attributeTypes.isEmpty());
	}

	/**
	 * 判断是不是当前Controller上的@SessionAttributes管理的属性值，是的话返回ture，否则返回false
	 *
	 * Whether the attribute name or type match the names and types specified
	 * via {@code @SessionAttributes} on the underlying controller.
	 * <p>Attributes successfully resolved through this method are "remembered"
	 * and subsequently used in {@link #retrieveAttributes(WebRequest)} and
	 * {@link #cleanupAttributes(WebRequest)}.
	 * @param attributeName the attribute name to check
	 * @param attributeType the type for the attribute
	 */
	public boolean isHandlerSessionAttribute(String attributeName, Class<?> attributeType) {
		Assert.notNull(attributeName, "Attribute name must not be null");
		// 判断是不是当前Controller上的@SessionAttributes管理的属性值，是的话返回ture，否则返回false
		if (this.attributeNames.contains(attributeName) || this.attributeTypes.contains(attributeType)) {
			// ⚠️
			this.knownAttributeNames.add(attributeName);
			return true;
		}
		else {
			return false;
		}
	}

	/**
	 * Store a subset of the given attributes in the session. Attributes not
	 * declared as session attributes via {@code @SessionAttributes} are ignored.
	 * @param request the current request
	 * @param attributes candidate attributes for session storage
	 */
	public void storeAttributes(WebRequest request, Map<String, ?> attributes) {
		// 遍历我们之前往ModelAndViewContainer中设置的所有属性值。
		// 其实之前调用ModelAndViewContainer#addAtrtribute设置属性值，就是调用Model，往Model中设置属性值！
		attributes.forEach((name, value) -> {
			// 判断Model中的"属性名称"或者"属性值的类型"，是否有在当前Controller的@SessionAttributes中设置
			// 如果有，就把Model中的属性名称和属性值，往session作用域中设置
			if (value != null && isHandlerSessionAttribute(name, value.getClass())) {
				this.sessionAttributeStore.storeAttribute(request, name, value);
			}
		});
	}

	/**
	 * Retrieve "known" attributes from the session, i.e. attributes listed
	 * by name in {@code @SessionAttributes} or attributes previously stored
	 * in the model that matched by type.
	 *
	 * 从会话中检索“已知”属性，即 {@code @SessionAttributes} 中按名称列出的属性，或先前存储在model模型中的按类型匹配的属性。
	 *
	 * @param request the current request
	 * @return a map with handler session attributes, possibly empty
	 */
	public Map<String, Object> retrieveAttributes(WebRequest request) {
		Map<String, Object> attributes = new HashMap<>();
		for (String name : this.knownAttributeNames) {
			// 从session作用域获取属性值
			Object value = this.sessionAttributeStore.retrieveAttribute(request, name);
			// 如果session作用域中存在属性值，就放入map中
			if (value != null) {
				attributes.put(name, value);
			}
		}
		return attributes;
	}

	/**
	 * Remove "known" attributes from the session, i.e. attributes listed
	 * by name in {@code @SessionAttributes} or attributes previously stored
	 * in the model that matched by type.
	 * @param request the current request
	 */
	public void cleanupAttributes(WebRequest request) {
		// 遍历knownAttributeNames
		for (String attributeName : this.knownAttributeNames) {
			// 从session作用域中移除当前属性
			this.sessionAttributeStore.cleanupAttribute(request, attributeName);
		}
	}

	/**
	 * A pass-through call to the underlying {@link SessionAttributeStore}.
	 * @param request the current request
	 * @param attributeName the name of the attribute of interest
	 * @return the attribute value, or {@code null} if none
	 */
	@Nullable
	Object retrieveAttribute(WebRequest request, String attributeName) {
		return this.sessionAttributeStore.retrieveAttribute(request, attributeName);
	}

}
