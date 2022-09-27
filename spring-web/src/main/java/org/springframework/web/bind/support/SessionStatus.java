/*
 * Copyright 2002-2007 the original author or authors.
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

package org.springframework.web.bind.support;

/**
 * 看下， Session的处理工作是否完成
 *
 * Simple interface that can be injected into handler methods, allowing them to
 * signal that their session processing is complete. The handler invoker may
 * then follow up with appropriate cleanup, e.g. of session attributes which
 * have been implicitly created during this handler's processing (according to
 * the
 * {@link org.springframework.web.bind.annotation.SessionAttributes @SessionAttributes}
 * annotation).
 *
 * 可以注入处理程序方法的简单接口，允许它们发出会话处理完成的信号。
 * 然后处理程序调用者可以跟进适当的清理，例如在此处理程序处理期间隐式创建的会话属性
 * （根据 {@link org.springframework.web.bind.annotation.SessionAttributes @SessionAttributes} 注释）。
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see org.springframework.web.bind.annotation.RequestMapping
 * @see org.springframework.web.bind.annotation.SessionAttributes
 */
public interface SessionStatus {

	/**
	 * Mark the current handler's session processing as complete, allowing for
	 * cleanup of session attributes.
	 *
	 * 将当前处理程序的会话处理标记为完成，以便清理会话属性。
	 */
	void setComplete/* 设置完成 */();

	/**
	 * Return whether the current handler's session processing has been marked
	 * as complete.
	 *
	 * 返回当前处理程序的会话处理是否已标记为完成。
	 */
	boolean isComplete/* 是否完成 */();

}
