package com.springstudy.msb.s_06.selftag;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 自定义标签
 * @date 2021/12/16 12:42 上午
 */
public class Main {

	/*

	1、BeanDefinitionReader：配置文件的读取器，也就是加载配置文件用的。

	例如：XmlBeanDefinitionReader就是读取xml配置文件，形成一个Document对象
	例如：PropertiesBeanDefinitionReader读取properties配置文件，形成一个Properties对象

	题外：我们定义配置文件的方式可以是xml、properties、yml等其它方式，所以有这么一个规范

	2、BeanDefinitionDocumentReader：处理Document用的。

	例如：DefaultBeanDefinitionDocumentReader，处理Document中默认命名空间的标签，就是由该对象处理的；然后调用BeanDefinitionParserDelegate去处理其它命名空间的标签也是由该对象处理的

	3、BeanDefinitionParserDelegate：bd解析委托器。解析自定义标签（自定义标签，也叫：其它命名空间标签）用的。通过它去找自定义标签命名空间，然后再调用命名空间解析标签，命名空间内部找标签解析器，然后调用标签解析器

	4、NameSpaceHandler：命名空间的顶层接口

	5、BeanDefinitionParser：bd解析器的顶层接口，用于解析标签、注解，形成bd

	 */

	/**
	 * 1、ApplicationContext#loadBeanDefinitions()
	 *
	 * 题外：里面创建了BeanDefinitionReader = XmlBeanDefinitionReader
	 *
	 * 2、——> XmlBeanDefinitionReader#loadBeanDefinitions()
	 *
	 * 验证和构建xml文件为一个Document对象
	 *
	 * 题外：里面创建BeanDefinitionDocumentReader = DefaultBeanDefinitionDocumentReader
	 *
	 * 3、——> BeanDefinitionDocumentReader#parseBeanDefinitions()
	 *
	 * 处理Document中的元素（也就是处理xml中的标签），解析成为bd，和注册bd
	 *
	 * （1）默认命名空间的标签走的是BeanDefinitionDocumentReader中的方法进行解析
	 * （2）其它命名空间的标签走的是BeanDefinitionParserDelegate进行解析
	 *
	 * 题外：里面创建了BeanDefinitionParserDelegate
	 */

	/**
	 * 步骤1、查找到对应的xsd文件，检验是否符合对应的规范，符合规范才能得到一个xml文件的document对象
	 *
	 * spring.schemas里面的配置的内容是：xsd文件所在的网络位置=本地xsd文件位置。用xsd文件来校验对应的"命名空间uri"的标签格式配置得是否正确！
	 * jdk在读取【spring-06-config.xml】的时候，会去通过【spring-06-config.xml】里面
	 * <beans: xsi:schemaLocation="http://www.mashibing.com/schema/user http://www.mashibing.com/schema/user.xsd">中配置的"命名空间uri"获取到对应的"网络xsd文件位置"；
	 * 然后通过"网络xsd文件位置"先去spring.schemas里面找"本地xsd文件位置"，找到了就去通过"本地xsd文件位置"读取对应的xsd文件；没有找到，就通过"网络xsd文件位置"，发送http请求，获取到对应的xsd文件。如果通过"网络xsd文件位置"还没有获取到对应的xsd文件，就报FileNotFoundException异常！
	 * xsd文件，是用于后续【spring-06-config.xml】文件中的标签格式校验
	 * 如果所有标签校验通过，【spring-06-config.xml】文件最终就会形成一个document对象
	 * <p>
	 * 项目代码：XmlBeanDefinitionReader#doLoadBeanDefinitions() ——> doLoadDocument() ——> loadDocument()里面
	 *
	 * <p>
	 * 步骤2、获取到handler处理器，通过处理器完成标签的解析工作
	 *
	 * spring.handlers里面配置的内容是：命名空间uri=NamespaceHandler。在第一次实例化NamespaceHandler事，会调用【NamespaceHandler#init()注册"标签"对应的"解析器"】，解析器是用于后面解析对应标签的属性，转换为一个BeanDefinition！
	 * 在读取完了document之后，会逐一把document里面的标签转化为对应的bd。
	 * 标签分为两种，一种是默认（默认命名空间uri的标签），一种是自定义（其它命名空间uri的标签）。
	 * 默认的标签的话，直接调用对应的方法进行解析（DefaultBeanDefinitionDocumentReader里面对应的方法进行解析），转化为bd
	 * 自定义的标签，在解析的时候，依赖于对应的标签解析器，而解析器又依赖于NamespaceHandler去进行初始化。
	 * 所以会先通过"命名空间uri"获取spring.handlers文件里面配置的对应的"NamespaceHandler命名空间处理器"，然后调用命名空间处理器的init()，初始化和注册该命名空间uri的所有标签对应的解析器（标签解析器）;
	 * （一个命名空间可以有很多个不同的标签，不同的标签采用不同的解析器，所以一个NamespaceHandler可以针对不同的标签定制不同的解析器！）
	 * 然后调用命名空间处理器进行解析，里面是获取该命名空间处理器中，对应的标签的对应解析器（标签解析器），然后通过标签解析器，解析标签属性值，形成一个bd，然后把bd放入beanFactory的beanDefinitionMap、beanDefinitionNames中
	 *
	 * <p>
	 * 项目代码：获取handler，和调用命名空间处理器，标签解析器，解析标签转化为bd的逻辑在：BeanDefinitionParserDelegate#parseCustomElement()
	 */
	public static void main(String[] args) {
		// dtd文件 / xsd文件
		ApplicationContext classPathXmlApplicationContext
				= new ClassPathXmlApplicationContext("msb/spring-06-selfTag.xml");
		User user = (User) classPathXmlApplicationContext.getBean("haha");
		System.out.println(user.getUsername());
	}

}