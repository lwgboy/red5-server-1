package org.red5.server;

import java.util.HashMap;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.core.io.Resource;

public class ContextLoader implements ApplicationContextAware {

	protected static Log log =
        LogFactory.getLog(ContextLoader.class.getName());
	
	protected ApplicationContext applicationContext;
	protected ApplicationContext parentContext;
	protected String contextsConfig;
	protected HashMap<String, ApplicationContext> contextMap = new HashMap<String,ApplicationContext>();
	
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;		
	}
	
	public void setParentContext(ApplicationContext parentContext) {
		this.parentContext = parentContext;
	}

	public void setContextsConfig(String contextsConfig) {
		this.contextsConfig = contextsConfig;
	}

	public void init() throws Exception {
		Properties props = new Properties();
		Resource res = applicationContext.getResource(contextsConfig);
		if(!res.exists()){
			log.error("Contexts config must be set.");
			return;
		}
    	
		props.load(res.getInputStream());
    	
    	for(Object key : props.keySet()){
    		String name = (String) key;
    		String config = props.getProperty(name);
    		log.debug("Loading: "+name+" = "+config);
    		loadContext(name, config);
    	}
    	
	}
	
	protected void loadContext(String name, String config){
		ApplicationContext context = new FileSystemXmlApplicationContext(new String[]{config}, parentContext);
		contextMap.put(name, context);
		// add the context to the parent, this will be red5.xml
		ConfigurableBeanFactory factory = ((ConfigurableApplicationContext) applicationContext).getBeanFactory();
		factory.registerSingleton(name,context);
	}
	
}