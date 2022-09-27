package com.springstudy.msb.s_11;

import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;

import java.text.MessageFormat;
import java.util.Locale;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/4/28 11:30 下午
 */
public class TestMessageSource implements MessageSource {

	/**
	 * 解析code对应的信息进行返回，如果对应的code不能被解析，则返回默认信息defaultMessage
	 * code:需要进行解析的code,对应资源文件中的一个属性名
	 * args:需要用来替换code对应的信息中包含参数的内容
	 * defaultMessage:当对应code的信息不存在时需要返回的默认值
	 * locale:对应的Locale对象
	 * <p>
	 * Try to resolve the message. Return default message if no message was found.
	 *
	 * @param code           the message code to look up, e.g. 'calculator.noRateSet'.
	 *                       MessageSource users are encouraged to base message names on qualified class
	 *                       or package names, avoiding potential conflicts and ensuring maximum clarity.
	 * @param args           an array of arguments that will be filled in for params within
	 *                       the message (params look like "{0}", "{1,date}", "{2,time}" within a message),
	 *                       or {@code null} if none
	 * @param defaultMessage a default message to return if the lookup fails
	 * @param locale         the locale in which to do the lookup
	 * @return the resolved message if the lookup was successful, otherwise
	 * the default message passed as a parameter (which may be {@code null})
	 * @see #getMessage(MessageSourceResolvable, Locale)
	 * @see MessageFormat
	 */
	@Override
	public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
		return null;
	}

	/**
	 * 解析code对应的信息进行返回，如果对应的code不能被解析则抛出异常NoSuchMessageException
	 * code:需要进行解析的code,对应资源文件中的个属性名
	 * args:需要用来替换code对应的信息中包含参数的内容
	 * locale:对应的Locale对象
	 * <p>
	 * Try to resolve the message. Treat as an error if the message can't be found.
	 *
	 * @param code   the message code to look up, e.g. 'calculator.noRateSet'.
	 *               MessageSource users are encouraged to base message names on qualified class
	 *               or package names, avoiding potential conflicts and ensuring maximum clarity.
	 * @param args   an array of arguments that will be filled in for params within
	 *               the message (params look like "{0}", "{1,date}", "{2,time}" within a message),
	 *               or {@code null} if none
	 * @param locale the locale in which to do the lookup
	 * @return the resolved message (never {@code null})
	 * @throws NoSuchMessageException if no corresponding message was found
	 * @see #getMessage(MessageSourceResolvable, Locale)
	 * @see MessageFormat
	 */
	@Override
	public String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
		return null;
	}

	/**
	 * 通过传递的MessageSourceResolvable对应来解析对应的信息
	 * <p>
	 * Try to resolve the message using all the attributes contained within the
	 * {@code MessageSourceResolvable} argument that was passed in.
	 * <p>NOTE: We must throw a {@code NoSuchMessageException} on this method
	 * since at the time of calling this method we aren't able to determine if the
	 * {@code defaultMessage} property of the resolvable is {@code null} or not.
	 *
	 * @param resolvable the value object storing attributes required to resolve a message
	 *                   (may include a default message)
	 * @param locale     the locale in which to do the lookup
	 * @return the resolved message (never {@code null} since even a
	 * {@code MessageSourceResolvable}-provided default message needs to be non-null)
	 * @throws NoSuchMessageException if no corresponding message was found
	 *                                (and no default message was provided by the {@code MessageSourceResolvable})
	 * @see MessageSourceResolvable#getCodes()
	 * @see MessageSourceResolvable#getArguments()
	 * @see MessageSourceResolvable#getDefaultMessage()
	 * @see MessageFormat
	 */
	@Override
	public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
		return null;
	}
}