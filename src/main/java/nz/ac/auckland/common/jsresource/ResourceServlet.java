package nz.ac.auckland.common.jsresource;

import nz.ac.auckland.lmz.flags.Flags;
import nz.ac.auckland.util.JacksonHelperApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;

import javax.inject.Inject;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Author: Marnix
 * <p>
 * This servlet is able to serve up application resources for either global or session
 * specific application resources.
 */
public class ResourceServlet extends HttpServlet {

	/**
	 * The default global namespace of javascript resources
	 */
	protected String namespace = ResourceNamespace.DEFAULT_RESOURCE_NAMESAPCE;

	@Inject
	protected ResourceNamespace resourceNamespace;

	@Inject
	protected JacksonHelperApi jacksonHelperApi;

	/**
	 * Application resources
	 */
	@Autowired(required = false)
	protected List<ApplicationResource> applicationResources;

	/**
	 * Cached results.
	 */
	protected String globalResults = null;

	/**
	 * Initialize the servlet by binding the spring attributes to it
	 *
	 * @param config is the servlet configuration
	 * @throws ServletException
	 */
	@Override
	public void init(ServletConfig config) throws ServletException {
		SpringBeanAutowiringSupport.processInjectionBasedOnServletContext(this, config.getServletContext());
		namespace = resourceNamespace.getNamespace();
	}

	/**
	 * Retrieve a specific bundle
	 *
	 * @param request  is the incoming request instance
	 * @param response is the outgoing response instance
	 * @throws javax.servlet.ServletException
	 * @throws IOException
	 */
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		ResourceScope scope = getResourceScopeForPath((request == null ? null : request.getPathInfo()));

		// setup response headers
		this.setResponseHeaders(response, scope);

		Writer responseWriter = this.getResponseWriter(response);

		// no writer?
		if (responseWriter == null) {
			throw new IllegalStateException("Cannot write without a proper writer instance");
		}

		if (isInDevMode() || scope == ResourceScope.Session) {
			this.writeResourcesForScope(responseWriter, scope);
		} else if (scope == ResourceScope.Global) {

			// not yet rendered?
			if (this.globalResults == null) {
				StringWriter stringWriter = new StringWriter();
				this.writeResourcesForScope(stringWriter, scope);
				this.globalResults = stringWriter.toString();
			}

			responseWriter.write(this.globalResults);
		} else if (scope == ResourceScope.Unknown) {
			this.writeErrorComment(responseWriter);
		}
	}

	/**
	 * Write the results of application resources for a scope.
	 *
	 * @param writer is the writer to output to
	 * @param scope  is the scope to collect information for
	 */
	protected void writeResourcesForScope(final Writer writer, final ResourceScope scope) throws IOException {

		// find resources for this scope
		List<ApplicationResource> scopeResources = new ArrayList<ApplicationResource>();

		if (applicationResources != null) {
			for (ApplicationResource applicationResource : applicationResources) {
				if (applicationResource.getResourceScope().equals(scope)) {
					scopeResources.add(applicationResource);
				}
			}
		}

		this.writeHeader(writer);

		for (ApplicationResource resource : scopeResources) {
			writeResource(writer, resource);
		}

	}

	/**
	 * Set response headers
	 * <p>
	 * http://stackoverflow.com/questions/4480304/how-to-set-http-headers-for-cache-control
	 */
	protected void setResponseHeaders(HttpServletResponse response, ResourceScope scope) {
		response.setHeader("Content-Type", "text/javascript; charset=utf-8");

		// cache for a month
		if (scope.equals(ResourceScope.Global)) {
			// 480 weeks
			response.setHeader("Cache-Control", "max-age=290304000, public");
		} else if (scope.equals(ResourceScope.Session)) {
			// if a session resource, make sure to not make cacheable.
			response.setHeader("Cache-Control", "no-cache, must-revalidate");
			response.setHeader("Pragma", "no-cache");
			response.setHeader("Expires", "Sat, 26 Jul 1997 05:00:00 GMT");

		}

	}

	/**
	 * @return the response writer instance we're using to write results to
	 */
	protected Writer getResponseWriter(HttpServletResponse response) throws IOException {
		return (response == null ? null : response.getWriter());
	}

	/**
	 *
	 */
	protected void writeErrorComment(Writer writer) throws IOException {
		writer.write("/* unable to render unknown scope */\n");
	}

	/**
	 * Write a string that makes sure we're not writing into an empty UOA namespace
	 *
	 * @param writer is the writer to write to
	 */
	protected void writeHeader(Writer writer) throws IOException {
		writer.write(String.format("if (!window.%s) { window.%s = {}; }\n", namespace, namespace));
	}

	/**
	 * Write the contents of an application resource
	 *
	 * @param writer   is the write to write to
	 * @param resource is the resource to write
	 */
	protected void writeResource(final Writer writer, ApplicationResource resource) throws IOException {
		if (resource != null && resource.getResourceMap() != null) {
			for (Map.Entry<String, Object> item : resource.getResourceMap().entrySet()) {
				assert jacksonHelperApi != null;
				writer.write(String.format("%s.%s = %s;\n", namespace, item.getKey(), jacksonHelperApi.jsonSerialize(item.getValue())));
			}
		}
	}

	/**
	 * @return true if the app has been started in development mode. If this is the case, never
	 * cache anything.
	 */
	protected boolean isInDevMode() {
		return Flags.DEVMODE.on();
	}

	/**
	 * Return the resource scope we're trying got render for the current path
	 *
	 * @param pathInfo is the path relative to the base of the servlet we're rendering from
	 * @return a resource scope, returns Unknown when not able to determine what scope.
	 */
	protected ResourceScope getResourceScopeForPath(String pathInfo) {
		ResourceScope scope = ResourceScope.Unknown;

		if (pathInfo != null) {
			if (pathInfo.endsWith("/global.js")) {
				scope = ResourceScope.Global;
			}

			if (pathInfo.endsWith("/session.js")) {
				scope = ResourceScope.Session;
			}
		}


		return scope;
	}

}
