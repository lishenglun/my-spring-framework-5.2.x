/*
 * Copyright 2002-2019 the original author or authors.
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

import org.springframework.beans.BeanUtils;
import org.springframework.core.Conventions;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpSessionRequiredException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Assist with initialization of the {@link Model} before controller method
 * invocation and with updates to it after the invocation.
 *
 * <p>On initialization the model is populated with attributes temporarily stored
 * in the session and through the invocation of {@code @ModelAttribute} methods.
 *
 * <p>On update model attributes are synchronized with the session and also
 * {@link BindingResult} attributes are added if missing.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public final class ModelFactory {

	// 里面存储了，当前Controller中的，和全局的标注了【@ModelAttribute】，但是没有标注@RequestMapping的方法
	private final List<ModelMethod> modelMethods/* 可调用的HandlerMethod */ = new ArrayList<>();

	// 里面存储了，当前Controller中的，和全局的标注了【@InitBinder】的方法
	private final WebDataBinderFactory dataBinderFactory/* 数据绑定工厂 */;

	// 里面存储了，当前Controller上的，所有的【@SessionAttributes】里面的属性值
	private final SessionAttributesHandler sessionAttributesHandler/* Session属性处理器 */;

	/**
	 * Create a new instance with the given {@code @ModelAttribute} methods.
	 * @param handlerMethods the {@code @ModelAttribute} methods to invoke
	 * @param binderFactory for preparation of {@link BindingResult} attributes
	 * @param attributeHandler for access to session attributes
	 */
	public ModelFactory(@Nullable List<InvocableHandlerMethod> handlerMethods,
			WebDataBinderFactory binderFactory, SessionAttributesHandler attributeHandler) {

		// 1、handlerMethods：里面包含了，当前Controller中的，和全局的（@ControllerAdvice），标注了@ModelAttribute，但是没有标注@RequestMapping的方法
		if (handlerMethods != null) {
			for (InvocableHandlerMethod handlerMethod : handlerMethods) {
				this.modelMethods.add(new ModelMethod(handlerMethod));
			}
		}

		// 2、WebDataBinderFactory：数据绑定工厂。里面包含了，当前Controller中的，和全局的（@ControllerAdvice），标注了@InitBinder的方法
		this.dataBinderFactory = binderFactory;

		// 3、SessionAttributesHandler：Session属性处理器。里面包含了，当前Controller上的，所有的@SessionAttributes注解的属性值
		this.sessionAttributesHandler = attributeHandler;
	}


	/**
	 * Populate the model in the following order:
	 * <ol>
	 * <li>Retrieve "known" session attributes listed as {@code @SessionAttributes}.
	 * <li>Invoke {@code @ModelAttribute} methods
	 * <li>Find {@code @ModelAttribute} method arguments also listed as
	 * {@code @SessionAttributes} and ensure they're present in the model raising
	 * an exception if necessary.
	 * </ol>
	 * @param request the current request
	 * @param container a container with the model to be initialized
	 * @param handlerMethod the method for which the model is initialized
	 * @throws Exception may arise from {@code @ModelAttribute} methods
	 */
	public void initModel(NativeWebRequest request, ModelAndViewContainer container, HandlerMethod handlerMethod)
			throws Exception {

		// 都设置到ModelAndView容器里面，后面直接调用即可
		// ModelAndView：相当于整个请求链路中的数据上下文

		/* 1、根据之前解析好的当前Controller上的@SessionAttributes的属性名，去session作用域中获取对应的属性值，然后将获取到的属性值放入ModelAndViewContainer中 */

		// 根据我们的request，获取HttpSession；然后再根据@SessionAttributes中配置的属性名，去Session作用域中检索对应的属性值
		Map<String, ?> sessionAttributes = this.sessionAttributesHandler.retrieveAttributes/* 检索属性 */(request);
		container.mergeAttributes(sessionAttributes);

		/* 2、执行当前Controller中的，和全局的，@ModelAttribute标注的方法，并将返回值设置到ModelAndViewContainer中 */
		/**
		 * 题外：@ModelAttribute就是方法之间进行相互调用的时候，参数能不能传递过来，当我设置到Model去之后，方便我进行参数传递，
		 * 在其它方法里面能够调用到我们具体的属性值，所以往Model里面进行相关的值设置，设置完值之后，就能保证后面的方法能够直接获取
		 */
		// 执行@ModelAttribute标注的方法，并将结果设置到ModelAndViewContainer中
		// 题外：如果设置了@ModelAttribute的name属性，和存在返回值。那么key就是name属性值，value则是返回值。
		invokeModelAttributeMethods(request, container);

		/*

		3、判断控制器方法(Controller中具体处理请求的方法)的参数，是否被@ModelAttribute修饰，
		>>> 如果方法参数被@ModelAttribute修饰，并且@ModelAttribute中设置的"参数名"在当前Controller上的@SessionAttributes中有设置，
		>>> 则根据@ModelAttribute中设置的"参数名"去"session作用域"里面获取对应的参数值，然后设置到ModelAndViewContainer中

		*/
		// 遍历既注释了@ModelAttribute又在@SessionAttributes注释中的参数
		for (String name : findSessionAttributeArguments/* 查找会话属性参数 */(handlerMethod)) {
			if (!container.containsAttribute(name)) {
				/* 容器中不存在该参数名 */

				// （1）去session作用域中，根据@ModelAttribute中设置的"参数名"获取参数值
				Object value = this.sessionAttributesHandler.retrieveAttribute/* 检索属性 */(request, name);
				if (value == null) {
					throw new HttpSessionRequiredException("Expected session attribute '" + name + "'", name);
				}
				// （2）把参数值设置到ModelAndViewContainer中
				container.addAttribute(name, value);
			}
		}
	}

	/**
	 * Invoke model attribute methods to populate the model.
	 * Attributes are added only if not already present in the model.
	 */
	private void invokeModelAttributeMethods(NativeWebRequest request, ModelAndViewContainer container)
			throws Exception {

		while (!this.modelMethods.isEmpty()) {
			/* 1、遍历获取@ModelAttriute标注的方法 */
			/**
			 * 1、getNextModelMethod(container)：依次从modelMethods中，获取ModelMethod
			 * 题外：ModelMethod是@ModelAttribute标注的方法
			 * 题外：里面的逻辑是，从第一个开始获取，然后移除第一个；接着下一次，从第一个开始获取的时候，就是下一个
			 *
			 * 2、getHandlerMethod()：从ModelMethod中获取InvocableHandlerMethod
			 * 题外：ModelMethod中的InvocableHandlerMethod也代表@ModelAttribute标注的方法
			 */
			// 获取第一个@ModelAttriute标注的方法，并从modelMethods中删除获取到的方法
			InvocableHandlerMethod modelMethod = getNextModelMethod(container).getHandlerMethod();

			/*

			2、判断当前的ModelAndViewContainer里面，是否包含了@ModelAttribute的name参数名。
			如果包含就跳过；并且如果该model方法是不允许数据绑定的，就加入到ModelAndViewContainer的"禁止数据绑定的参数名单"中。

			*/
			ModelAttribute ann = modelMethod.getMethodAnnotation(ModelAttribute.class);
			Assert.state(ann != null, "No ModelAttribute annotation");
			// 判断当前的ModelAndViewContainer里面，是否包含了@ModelAttribute的name参数名
			// 题外：@ModelAttribute中设置的name作为model的参数名
			if (container.containsAttribute(ann.name())) {
				/* 如果ModelAndView容器里面包含了@ModelAttribute的name参数名 */

				// 验证一下是否允许数据绑定
				// 如果不允许数据绑定，则将@ModelAttribute的name参数名，设置到ModelAndViewContainer中的"禁止数据绑定的参数名单"中，给禁用掉，表示该参数名禁止数据绑定
				if (!ann.binding()) {
					container.setBindingDisabled/* 设置绑定禁用的值 */(ann.name());
				}

				// 如果@ModelAttribute的name参数名，已经在ModelAndViewContainer中，则跳过
				continue;
			}

			/*

			3、如果ModelAndViewContainer中不包含@ModelAttribute的name参数名，就执行@ModelAttribute标注的方法，获取返回值

			*/

			/**
			 * 1、@ModelAttribute，是修饰在方法上面的，
			 * 方法可能会有对应的入参，所以在调用modelMethod.invokeForRequest()时，里面会先获取方法的参数；
			 * 以及方法可能会有返回值，所以会获取到返回值。返回值会作为value存储到ModelAndViewContainer中，如果@ModelAttribute配置了name，则name作为key
			 * （返回值作为value放到Model里面去，如果@ModelAttribute配置了name，它的key是name）
			 * 例如：
			 * @ControllerAdvice
			 * public class ControllerAdviceController {
			 *
			 *     // 全局数据绑定
			 *     @ModelAttribute(name="md")
			 *     public Map<String,Object> mydata(){
			 *         HashMap<String,Object> map = new HashMap<>();
			 *         map.put("age",99);
			 *         map.put("gender","男");
			 *         return map;
			 *     }
			 *
			 * }
			 * key是"@ModelAttribute中的name参数名，md"，value是"返回值Map"，存入到Model里面去
			 */
			// 执行@ModelAttribute标注的方法。returnValue是返回值。
			Object returnValue = modelMethod.invokeForRequest(request, container);

			/*

			4、判断@ModelAttribute标注的方法的返回值是否是void类型
			（1）如果是void类型，则代表方法自己将参数设置到model中，不处理
			（2）如是Void类型，则会将方法的返回值存入到ModelAndViewContainer中。
				key = 参数名称
				value = 返回值

			 */
			if (!modelMethod.isVoid()){
				/* 如果@ModelAttribute方法的返回值类型不是Void */

				/**
				 * 1、参数名的生成规则
				 * （1）如果@ModelAttribute中定义了value,就以value命名
				 * （2）如果注解中没有定义value,则根据返回值类型定义名称
				 */
				// 获取参数名
				String returnValueName = getNameForReturnValue(returnValue, modelMethod.getReturnType());

				// 验证一下是否允许数据绑定
				// 如果不允许数据绑定，则将该参数名，设置到ModelAndViewContainer中的"禁止数据绑定的参数名单"中
				if (!ann.binding()) {
					container.setBindingDisabled(returnValueName);
				}

				// 如果ModelAndViewContainer中不存在该参数名，
				// 则将该参数名作为key，@ModelAttribute方法的返回结果作为value，存入到ModelAndViewContainer中
				if (!container.containsAttribute(returnValueName)) {
					container.addAttribute(returnValueName, returnValue);
				}
			}
		}
	}

	private ModelMethod getNextModelMethod(ModelAndViewContainer container) {
		for (ModelMethod modelMethod : this.modelMethods) {
			if (modelMethod.checkDependencies/* 检查依赖项 */(container)) {
				this.modelMethods.remove(modelMethod);
				return modelMethod;
			}
		}

		// 获取modelMethods中的第一个ModelMethod，然后从modelMethods中移除
		ModelMethod modelMethod = this.modelMethods.get(0);
		this.modelMethods.remove(modelMethod);
		return modelMethod;
	}

	/**
	 * 获取，一个参数上有@ModelAttribute注解，并且该参数的参数名，在当前Controller上的@SessionAttributes属性中有配置的参数名。
	 *
	 * 获取同时有@ModelAttribute注解又有@SessionAttributes注解中的参数
	 *
	 * Find {@code @ModelAttribute} arguments also listed as {@code @SessionAttributes}.
	 *
	 * 查找也列为 {@code @SessionAttributes} 的 {@code @ModelAttribute} 参数。
	 */
	private List<String> findSessionAttributeArguments(HandlerMethod handlerMethod) {
		List<String> result = new ArrayList<>();
		// 遍历方法参数
		for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
			// 如果参数上有@modelAttribute注解
			if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
				// 获取参数名
				// 题外：如果配置了@ModelAttribute的name属性，则参数名就是name属性；否则通过根据参数类型来获取参数名称
				String name = getNameForParameter(parameter);
				// 获取参数类型
				Class<?> paramType = parameter.getParameterType();
				// 根据获取到的参数名和参数类型，检查"参数名和参数类型"中的一个是否也在当前Controller上的@SessionAttributes注解中
				if (this.sessionAttributesHandler.isHandlerSessionAttribute/* 是Handler的会话属性 */(name, paramType)) {
					// 如果存在当前Controller上的@SessionAttributes注解中，则将参数名称放入集合中
					result.add(name);
				}
			}
		}
		return result;
	}

	/**
	 * Promote model attributes listed as {@code @SessionAttributes} to the session.
	 * Add {@link BindingResult} attributes where necessary.
	 *
	 * 将列为{@code @SessionAttributes}的模型属性提升到会话。在必要时添加{@link BindingResult}属性。
	 *
	 * @param request the current request
	 * @param container contains the model to update
	 * @throws Exception if creating BindingResult attributes fails
	 */
	public void updateModel(NativeWebRequest request, ModelAndViewContainer container) throws Exception {
		// 获取defaultModel
		ModelMap defaultModel = container.getDefaultModel();

		/* 1、如果session的状态是已完成（会话处理已完成），则从session作用域中，清除当前Controller上的@SessionAttribute中的属性名 */

		// 判断会话处理是否完成
		// 对SessionAttributes进行设置，如果处理器里调用了setComplete则将SessionAttribute清空，否则将defaultModel中的参数设置到SessionAttributes中
		// 也就是说，我们为了保证参数的统一性，往Model里面设置一些数据
		if (container.getSessionStatus().isComplete()){
			// 如果已经完成了@SessionAttributes的属性处理，那么这块就会把它给清空掉
			this.sessionAttributesHandler.cleanupAttributes(request);
		}

		/* 2、如果session的状态是未完成，则从defaultModel的数据中，获取与当前Controller上的@SessionAttribute中设置的属性名对应数据，存入session作用于中 */
		// 将mavContainer的defaultModel中的参数值，设置到SessionAttributes
		else {
			// 如果defaultModel中的属性名称或者属性值，有在当前Controller的@SessionAttributes中配置，则把defaultModel中的该属性名称和属性值，放入到session作用域去
			// 🤔️方便我们在后面的SessionAttribute的时候进行调用
			this.sessionAttributesHandler.storeAttributes(request, defaultModel);
		}

		/* 3、如果请求没有处理完成，并且ModelAndViewContainer中的model等defaultModel于，就更新绑定结果值 */

		// 判断请求是否已经处理完或者是redirect类型的返回值，其实就是判断是否需要进行页面的渲染操作
		if (!container.isRequestHandled() && container.getModel() == defaultModel) {
			// 更新绑定结果值
			updateBindingResult(request, defaultModel);
		}
	}

	/**
	 * 如果处理器绑定参数时注释了@Valid和@Validated注解，那么会讲校验的结果设置到BindingResult类型的参数中，如果没有添加校验的注释，为了渲染方便，ModelFactory
	 * 会给Model设置一个跟参数相对应的BindingResult
	 *
	 * Add {@link BindingResult} attributes to the model for attributes that require it.
	 */
	private void updateBindingResult(NativeWebRequest request, ModelMap model) throws Exception {
		List<String> keyNames = new ArrayList<>(model.keySet());
		for (String name/* key */ : keyNames) {
			Object value/* value */ = model.get(name);
			// 遍历每一个Model中保存的参数，判断是否需要添加BindingResult，如果需要则使用WebDataBinder获取BindingResult并添加到Model，在添加前
			// 检查Model中是否已经存在，如果已经存在就不添加了
			if (value != null && isBindingCandidate(name, value)) {
				// 告诉它，它是一个返回的结果值
				String bindingResultKey = BindingResult.MODEL_KEY_PREFIX/* org.springframework.validation.BindingResult. */ + name;
				// 如果model中不存在bindingResult
				if (!model.containsAttribute(bindingResultKey)) {
					// 通过dataBinderFactory创建webDataBinder
					WebDataBinder dataBinder = this.dataBinderFactory.createBinder(request, value, name);
					// 添加到model
					model.put(bindingResultKey, dataBinder.getBindingResult());
				}
			}
		}
	}

	/**
	 * 判断是都需要添加BindingResult对象
	 *
	 * Whether the given attribute requires a {@link BindingResult} in the model.
	 */
	private boolean isBindingCandidate(String attributeName, Object value) {
		// 判断是不是其他参数绑定结果的BindingResult，如果是，则不需要添加
		if (attributeName.startsWith(BindingResult.MODEL_KEY_PREFIX/* org.springframework.validation.BindingResult. */)) {
			return false;
		}

		// 判断是不是由当前Controller上的@SessionAttribute管理的属性，如果是返回true
		if (this.sessionAttributesHandler.isHandlerSessionAttribute(attributeName, value.getClass())) {
			return true;
		}

		// 如果不是Array，不是Collection，不是Map，不是简单类型，则返回tur，否则返回false
		return (!value.getClass().isArray() && !(value instanceof Collection) &&
				!(value instanceof Map) && !BeanUtils.isSimpleValueType(value.getClass()));
	}


	/**
	 * Derive the model attribute name for the given method parameter based on
	 * a {@code @ModelAttribute} parameter annotation (if present) or falling
	 * back on parameter type based conventions.
	 * @param parameter a descriptor for the method parameter
	 * @return the derived name
	 * @see Conventions#getVariableNameForParameter(MethodParameter)
	 */
	public static String getNameForParameter(MethodParameter parameter) {
		// 如果配置了@ModelAttribute的name属性，则参数名就是name属性；否则通过根据参数类型来获取参数名称
		ModelAttribute ann = parameter.getParameterAnnotation(ModelAttribute.class);
		String name = (ann != null ? ann.value() : null);
		return (StringUtils.hasText(name) ? name : Conventions.getVariableNameForParameter(parameter));
	}

	/**
	 * 获取返回值的参数名
	 *
	 * Derive the model attribute name for the given return value. Results will be
	 * based on:
	 * <ol>
	 * <li>the method {@code ModelAttribute} annotation value
	 * <li>the declared return type if it is more specific than {@code Object}
	 * <li>the actual return value type
	 * </ol>
	 * @param returnValue the value returned from a method invocation
	 * @param returnType a descriptor for the return type of the method
	 * @return the derived name (never {@code null} or empty String)
	 */
	public static String getNameForReturnValue(@Nullable Object returnValue, MethodParameter returnType) {
		/*

		1、参数名称的生成规则
		（1）如果@ModelAttribute中定义了value,就以value命名
		（2）如果注解中没有定义value,则根据返回值类型定义名称

		 */
		// （1）如果方法上的@ModelAttribute中，设置了value，就以value作为参数名返回
		ModelAttribute ann = returnType.getMethodAnnotation(ModelAttribute.class);
		if (ann != null && StringUtils.hasText(ann.value())) {
			return ann.value();
		}
		// （2）如果方法上的@ModelAttribute中，没有定义了value，则根据返回值类型定义名称
		else {
			Method method = returnType.getMethod/* 获取方法 */();
			Assert.state(method != null, "No handler method");
			Class<?> containingClass = returnType.getContainingClass/* 获取包含类 */();
			Class<?> resolvedType = GenericTypeResolver.resolveReturnType/* 解析返回类型 */(method, containingClass);
			// 使用Conventions的静态方法getVariableNameForReturnType根据方法、返回值类型和返回值获取参数名
			return Conventions.getVariableNameForReturnType(method, resolvedType, returnValue);
		}
	}


	/**
	 * 代表标注了@ModelAttribute的方法
	 */
	private static class ModelMethod {

		// @ModelAttribute标注的方法
		// 题外：ModelMethod中的InvocableHandlerMethod也代表@ModelAttribute标注的方法
		private final InvocableHandlerMethod handlerMethod;

		private final Set<String> dependencies = new HashSet<>();

		public ModelMethod(InvocableHandlerMethod handlerMethod) {
			this.handlerMethod = handlerMethod;
			for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
				if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
					this.dependencies.add(getNameForParameter(parameter));
				}
			}
		}

		public InvocableHandlerMethod getHandlerMethod() {
			return this.handlerMethod;
		}

		public boolean checkDependencies(ModelAndViewContainer mavContainer) {
			for (String name : this.dependencies) {
				//
				if (!mavContainer.containsAttribute(name)) {
					return false;
				}
			}
			return true;
		}

		@Override
		public String toString() {
			return this.handlerMethod.getMethod().toGenericString();
		}
	}

}
