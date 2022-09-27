/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.web.servlet.mvc.method.annotation;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.WebAsyncTask;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 处理webAsyncTask类型的返回值，用于异步请求，使用WebAsyncManager完成（对异步请求的相关支持）
 *
 * Handles return values of type {@link WebAsyncTask}.
 *
 * @author Rossen Stoyanchev
 * @since 3.2
 */
public class AsyncTaskMethodReturnValueHandler implements HandlerMethodReturnValueHandler {

	@Nullable
	private final BeanFactory beanFactory;


	public AsyncTaskMethodReturnValueHandler(@Nullable BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}


	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		return WebAsyncTask.class.isAssignableFrom(returnType.getParameterType());
	}

	@Override
	public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {

		if (returnValue == null) {
			mavContainer.setRequestHandled(true);
			return;
		}

		WebAsyncTask<?> webAsyncTask = (WebAsyncTask<?>) returnValue;
		if (this.beanFactory != null) {
			webAsyncTask.setBeanFactory(this.beanFactory);
		}
		// ⚠️启动task，异步执行
		WebAsyncUtils.getAsyncManager(webRequest).startCallableProcessing(webAsyncTask, mavContainer);
	}

}
