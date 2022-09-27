package com.springstudy.msb.other.annotation_log;

/**
 * Spring版本中注解的发展历史
 *
 * 1、spring1.0
 *
 * @Trsansaction
 *
 * 2、spring2.0
 *
 * @Component
 * @Controller
 * @Service
 * @Repository
 *
 * @Autowired
 * @Qualifier
 *
 * @Required
 *
 * @RequestMapping
 *
 * @Aspect
 *
 * @Enable**相关的注解
 *
 * 3、spring3.0
 *
 * @Configuration
 * @ImportResource：实现Java配置类和xml配置的混合使用，来实现平稳过度。这样我们在启动spring容器的时候，就可以只引入一个配置类进行启动即可。
 * @Import
 * @ComponentScan：
 *
 * 在Spring3.1版之前，还不能够完全实现去XML配置，因为配置扫描路径我们还只能在XML配置文件中通过<component-scan>标签来实现，
 * 在3.1版本到来的时候，提供了@ComponentScan，该注解的作用是替换掉<component-scan>标签，是注解编程很大的进步，也是Spring实现无配置文件的坚实基础
 *
 * 4、spring4.0
 *
 * @Conditional
 * @EventListener
 * @AliasFor
 * @CrossOrigin
 *
 * 5、spring5.0
 *
 * @Indexed：解决@ComponentScan的性能问题，提升检索效率，加快系统启动速度。
 *
 * 随着业务模块越来越多，越来越复杂，尤其在比较大的单体架构里面，java文件很多。系统启动的时候，基于我们扫描的路径，扫描加载的东西很多，我们需要去很多目录里面寻找加载java类，
 * 这里面检索和遍历的效率，随着java文件越来越多，效率就会越来越低，对系统启动的影响越来越大，所以@ComponentScan在启动时存在性能问题
 *
 * 我们程序有2个阶段，编译和运行。在编译的时候，我们的代码已经确定了，哪些类中有注解修饰，已经知道了，所以@Indexed就是在编译的时候，帮我们把已经有对应注解修饰的java类都收集起来，存储在一个文件里面去；
 * 当系统启动的时候，它就只需要去这一个索引文件里面找到要加载的java类的全路径即可，这样一来就把启动时的性能影响转接到编译时
 *
 * 当系统启动的时候，只需要加载这一个文件，通过一次IO，就找到了要注入容器中的这些Java类的全路径，就把扫描的过程给省略掉了，增加了系统启动速度
 *
 * 根据这个索引文件就可以找到所有要载入容器中的Java类。@Component里面内置了@Indexed，所以我们去添加组件注解的话，在spring5.0之后，编译的时候默认都会识别到要被扫描的Java文件，要生效只需要加上spring-context-indexer依赖即可
 */
public class AnnotationLogMain {


}