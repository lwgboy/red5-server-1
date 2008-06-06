package org.red5.server.tomcat;

/*
 * RED5 Open Source Flash Server - http://www.osflash.org/red5
 *
 * Copyright (c) 2006-2008 by respective authors (see below). All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation; either version 2.1 of the License, or (at your option) any later
 * version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along
 * with this library; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Loader;
import org.apache.catalina.Realm;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.realm.MemoryRealm;
import org.apache.catalina.startup.Embedded;
import org.red5.server.LoaderBase;
import org.red5.server.LoaderMBean;
import org.red5.server.api.IApplicationContext;
import org.red5.server.jmx.JMXAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.XmlWebApplicationContext;

/**
 * Red5 loader for Tomcat.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class TomcatLoader extends LoaderBase implements
		ApplicationContextAware, LoaderMBean {
	
	/**
	 * Filters directory content
	 */
	protected class DirectoryFilter implements FilenameFilter {
		/**
		 * Check whether file matches filter rules
		 * 
		 * @param dir	Directory
		 * @param name	File name
		 * @return true If file does match filter rules, false otherwise
		 */
		public boolean accept(File dir, String name) {
			File f = new File(dir, name);
			log.debug("Filtering: {} name: {}", dir.getName(), name);
			log.debug("Constructed dir: {}", f.getAbsolutePath());
			// filter out all non-directories that are hidden and/or not
			// readable
			boolean result = f.isDirectory() && f.canRead() && !f.isHidden();
			//nullify
			f = null;
			return result;
		}
	}

	// Initialize Logging
	private static Logger log = LoggerFactory.getLogger(TomcatLoader.class);
	
	static {
		log.info("Init tomcat");
		// root location for servlet container
		String serverRoot = System.getProperty("red5.root");
		log.info("Server root: {}", serverRoot);
		String confRoot = System.getProperty("red5.config_root");
		log.info("Config root: {}", confRoot);
		// set in the system for tomcat classes
		System.setProperty("tomcat.home", serverRoot);
		System.setProperty("catalina.home", serverRoot);
		System.setProperty("catalina.base", serverRoot);
		// create one embedded (server) and use it everywhere
		embedded = new Embedded();	
	}

	/**
	 * Base container host.
	 */
	protected Host host;

	/**
	 * Tomcat connector.
	 */
	protected Connector connector;

	/**
	 * Embedded Tomcat service (like Catalina).
	 */
	protected static Embedded embedded;

	/**
	 * Tomcat engine.
	 */
	protected static Engine engine;

	/**
	 * Tomcat realm.
	 */
	protected Realm realm;

	/**
	 * Valves 
	 */
	protected List<Valve> valves = new ArrayList<Valve>();
	
	/**
	 * Additional connection properties to be set at init.
	 */
	protected Map<String, String> connectionProperties = new HashMap<String, String>();
	
	/**
	 * Add context for path and docbase to current host.
	 * 
	 * @param path		Path
	 * @param docBase	Document base
	 * @return			Catalina context (that is, web application)
	 */
	public Context addContext(String path, String docBase) {
		log.debug("Add context - path: {} docbase: {}", path, docBase);
		org.apache.catalina.Context c = embedded.createContext(path, docBase);
		log.debug("Context name: {}", c.getName());
		host.addChild(c);
		LoaderBase.setRed5ApplicationContext(path, new TomcatApplicationContext(c));
		return c;
	}
	
	/**
	 * Remove context from the current host.
	 * 
	 * @param path		Path
	 */
	@Override
	public void removeContext(String path) {
		Container[] children = host.findChildren();
		for (Container c : children) {
			if (c instanceof StandardContext && c.getName().equals(path)) {
				try {
					((StandardContext) c).stop();
					host.removeChild(c);			
					break;
				} catch (Exception e) {
					log.error("Could not remove context: {}", c.getName(), e);
				}				
			}
		}
		IApplicationContext ctx = LoaderBase.removeRed5ApplicationContext(path);
		if (ctx != null) {
			ctx.stop();			
		} else {
			log.warn("Red5 application context could not be stopped, it was null for path: {}", path);
		}
	}	

	/**
	 * Get base host.
	 * 
	 * @return Base host
	 */
	public Host getBaseHost() {
		return host;
	}

	/**
	 * Return connector.
	 * 
	 * @return Connector
	 */
	public Connector getConnector() {
		return connector;
	}

	/**
	 * Getter for embedded object.
	 * 
	 * @return Embedded object
	 */
	public Embedded getEmbedded() {
		return embedded;
	}

	/**
	 * Return Tomcat engine.
	 * 
	 * @return Tomcat engine
	 */
	public Engine getEngine() {
		return engine;
	}

	/**
	 * Getter for realm.
	 * 
	 * @return Realm
	 */
	public Realm getRealm() {
		return realm;
	}

	/**
	 * Initialization.
	 */
	public void init() {
		log.info("Loading tomcat context");
		
		ClassLoader classloader = Thread.currentThread().getContextClassLoader();

		//set the classloader
		Loader loader = embedded.createLoader(classloader);

		engine = embedded.createEngine();
		engine.setDefaultHost(host.getName());
		engine.setName("red5Engine");
		engine.setParentClassLoader(classloader);
		
		host.setParentClassLoader(classloader);
		
		if (webappFolder == null) {
			// Use default webapps directory
			webappFolder = System.getProperty("red5.root") + "/webapps";
		}
		System.setProperty("red5.webapp.root", webappFolder);
		log.info("Application root: " + webappFolder);

		// scan for additional webapp contexts

		// Root applications directory
		File appDirBase = new File(webappFolder);
		// Subdirs of root apps dir
		File[] dirs = appDirBase.listFiles(new DirectoryFilter());
		// Search for additional context files
		for (File dir : dirs) {
			String dirName = '/' + dir.getName();
			// check to see if the directory is already mapped
			if (null == host.findChild(dirName)) {
				Context ctx = null;
				if ("/root".equals(dirName) || "/root".equalsIgnoreCase(dirName)) {
					log.debug("Adding ROOT context");
					ctx = addContext("/", webappFolder + dirName);
				} else {
					log.debug("Adding context from directory scan: {}", dirName);
					ctx = addContext(dirName, webappFolder + dirName);
				}
				if (ctx != null) {
    				Object ldr = ctx.getLoader();
    				if (ldr != null) {
    					if (ldr instanceof WebappLoader) {
    						log.debug("Replacing context loader");				
    						((WebappLoader) ldr).setLoaderClass("org.red5.server.tomcat.WebappClassLoader");
    					} else {
    						log.debug("Context loader was instance of {}", ldr.getClass().getName());
    					}
    				} else {
    					log.debug("Context loader was null");
    					WebappLoader wldr = new WebappLoader(classloader);
    					wldr.setLoaderClass("org.red5.server.tomcat.WebappClassLoader");
    					ctx.setLoader(wldr);
    				}
				}
			}
		}

		// Dump context list
		if (log.isDebugEnabled()) {
			for (Container cont : host.findChildren()) {
				log.debug("Context child name: " + cont.getName());
			}
		}

		// Set a realm		
		if (realm == null) {
			realm = new MemoryRealm();
		}	
		embedded.setRealm(realm);

		// use Tomcat jndi or not
		if (System.getProperty("catalina.useNaming") != null) {
			embedded.setUseNaming(Boolean.valueOf(System.getProperty("catalina.useNaming")));
		}

		// add the valves to the host
		for (Valve valve : valves) {
			log.debug("Adding host valve: {}", valve);
			((StandardHost) host).addValve(valve);
		}		
		
		// baseHost = embedded.createHost(hostName, appRoot);
		engine.addChild(host);

		// Add new Engine to set of Engine for embedded server
		embedded.addEngine(engine);

		// set connection properties
		for (String key : connectionProperties.keySet()) {
			log.debug("Setting connection property: {} = {}", key, connectionProperties.get(key));
			connector.setProperty(connectionProperties.get(key), key);
		}
		
		// Add new Connector to set of Connectors for embedded server,
		// associated with Engine
		embedded.addConnector(connector);
				
		// Start server
		try {
			log.info("Starting Tomcat servlet engine");	
			embedded.start();

			LoaderBase.setApplicationLoader(new TomcatApplicationLoader(embedded, host, applicationContext));
			
			for (Container cont : host.findChildren()) {
				if (cont instanceof StandardContext) {
					StandardContext ctx = (StandardContext) cont;
					
					ServletContext servletContext = ctx.getServletContext();
					log.debug("Context initialized: {}", servletContext.getContextPath());
					
					String prefix = servletContext.getRealPath("/");
					log.debug("Path: {}", prefix);

					try {
						Loader cldr = ctx.getLoader();
						log.debug("Loader type: {}", cldr.getClass().getName());
						ClassLoader webClassLoader = cldr.getClassLoader();
						log.debug("Webapp classloader: {}", webClassLoader);
						//create a spring web application context
						XmlWebApplicationContext appctx = new XmlWebApplicationContext();
						appctx.setClassLoader(webClassLoader);
						appctx.setConfigLocations(new String[]{"/WEB-INF/red5-*.xml"});
						appctx.setParent((ApplicationContext) applicationContext.getBean("default.context"));					
						appctx.setServletContext(servletContext);
						//set the root webapp ctx attr on the each servlet context so spring can find it later					
						servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, appctx);
						appctx.refresh();
						//check if we want to use naming
						if (embedded.isUseNaming()) {
    						/*
    						String ctxName = servletContext.getContextPath().replaceAll("/", "");
    						if (StringUtils.isEmpty(ctxName)) {
    							ctxName = "root";
    						}
    						log.debug("Context name for naming resources: {}", ctxName);
    						//
    						NamingResources res = ctx.getNamingResources();
    						if (res == null) {
    							res = new NamingResources();
    						}
    						//context name env var
    						ContextEnvironment env = new ContextEnvironment();
    						env.setDescription("JNDI logging context for this app");
    						env.setName("logback/context-name");
    						env.setType("java.lang.String");
    						env.setValue(ctxName);
    						// add to naming resources
    						res.addEnvironment(env);
    						//configuration resource - logger config file name
    						ContextEnvironment env2 = new ContextEnvironment();
    						env2.setDescription("URL for configuring logback context");
    						env2.setName("logback/configuration-resource");
    						env2.setType("java.lang.String");
    						env2.setValue("logback-" + ctxName + ".xml");
    						//
    						res.addEnvironment(env2);
    						//
    						ctx.setNamingResources(res);
    						*/
						} else {
							log.info("Naming (JNDI) is not enabled");
						}
					} catch (Throwable t) {
						log.error("Error setting up context: {}", servletContext.getContextPath(), t);
						//t.printStackTrace();
					}					
				}
			}
			
			//if everything is ok at this point then call the rtmpt and rtmps beans so they will init
			if (applicationContext.containsBean("red5.core")) {
				ApplicationContext core = (ApplicationContext) applicationContext.getBean("red5.core");			
    			if (core.containsBean("rtmpt.server")) {
    				log.debug("Initializing RTMPT");
    				core.getBean("rtmpt.server");
    				log.debug("Finished initializing RTMPT");    				
    			} else {
    				log.info("RTMPT server bean was not found");
    			}
    			if (core.containsBean("rtmps.server")) {
    				log.debug("Initializing RTMPS");
    				core.getBean("rtmps.server");				
    				log.debug("Finished initializing RTMPS");    				
    			} else {
    				log.info("RTMPS server bean was not found");
    			}
			} else {
				log.info("Core context was not found");
			}
		} catch (org.apache.catalina.LifecycleException e) {
			log.error("Error loading Tomcat", e);
		} finally {
			registerJMX();		
		}
		
	}

	/**
	 * Set base host.
	 * 
	 * @param baseHost	Base host
	 */
	public void setBaseHost(Host baseHost) {
		log.debug("setBaseHost");
		this.host = baseHost;
	}

	/**
	 * Set connector.
	 * 
	 * @param connector
	 *            Connector
	 */
	public void setConnector(Connector connector) {
		log.info("Setting connector: " + connector.getClass().getName());
		this.connector = connector;
	}

	/**
	 * Set additional connectors.
	 * 
	 * @param connectors
	 *            Additional connectors
	 */
	public void setConnectors(List<Connector> connectors) {
		log.debug("setConnectors: {}", connectors.size());
		for (Connector ctr : connectors) {
			embedded.addConnector(ctr);
		}
	}

	/**
	 * Set additional contexts.
	 * 
	 * @param contexts
	 *            Map of contexts
	 */
	public void setContexts(Map<String, String> contexts) {
		log.debug("setContexts: {}", contexts.size());
		for (String key : contexts.keySet()) {
			host.addChild(embedded.createContext(key, webappFolder
					+ contexts.get(key)));
		}
	}

	/**
	 * Setter for embedded object.
	 * 
	 * @param embedded
	 *            Embedded object
	 */
	public void setEmbedded(Embedded embedded) {
		log.info("Setting embedded: {}", embedded.getClass().getName());
		TomcatLoader.embedded = embedded;
	}
	
	/**
	 * Get the host.
	 * 
	 * @return host
	 */
	public Host getHost() {
		return host;
	}	
	
	/**
	 * Set the host.
	 * 
	 * @param host
	 */
	public void setHost(Host host) {
		log.debug("setHost");
		this.host = host;
	}	

	/**
	 * Set additional hosts.
	 * 
	 * @param hosts	List of hosts added to engine
	 */
	public void setHosts(List<Host> hosts) {
		log.debug("setHosts: {}", hosts.size());
		for (Host h : hosts) {
			engine.addChild(h);
		}
	}

	/**
	 * Setter for realm.
	 * 
	 * @param realm
	 *            Realm
	 */
	public void setRealm(Realm realm) {
		log.info("Setting realm: {}", realm.getClass().getName());
		this.realm = realm;
	}

	/**
	 * Set additional valves.
	 * 
	 * @param valves
	 *            List of valves
	 */
	public void setValves(List<Valve> valves) {
		log.debug("setValves: {}", valves.size());
		this.valves.addAll(valves);
	}
	
	/**
	 * Set connection properties for the connector
	 * 
	 * @param mappings
	 */
	public void setConnectionProperties(Map<String, String> props) {
		log.debug("Connection props: {}", props.size());
		this.connectionProperties.putAll(props);
	}		

	public void registerJMX() {
		JMXAgent.registerMBean(this, this.getClass().getName(),	LoaderMBean.class);	
	}
	
	/**
	 * Shut server down.
	 */
	public void shutdown() {
		log.info("Shutting down Tomcat context");
		JMXAgent.shutdown();
		try {
			embedded.stop();
			System.exit(0);
		} catch (Exception e) {
			log.warn("Tomcat could not be stopped", e);
			System.exit(1);
		}
	}

}