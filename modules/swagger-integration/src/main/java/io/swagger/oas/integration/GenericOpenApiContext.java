package io.swagger.oas.integration;

import io.swagger.oas.models.OpenAPI;
import io.swagger.oas.models.info.Info;
import io.swagger.oas.web.OpenApiReader;
import io.swagger.oas.web.OpenApiScanner;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GenericOpenApiContext<T extends GenericOpenApiContext> implements OpenApiContext {

    private OpenApiConfiguration openApiConfiguration;

    protected String resourcePackageNames;

    private String basePath = "/";

    private Map<String, OpenApiProcessor> openApiProcessors = new HashMap<String, OpenApiProcessor>();

    protected String id = OPENAPI_CONTEXT_ID_DEFAULT;
    protected OpenApiContext parent;


    public String getResourcePackageNames() {
        return resourcePackageNames;
    }

    public T resourcePackageNames(String resourcePackageNames) {
        this.resourcePackageNames = resourcePackageNames;
        return (T) this;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public final T basePath(String basePath) {
        this.basePath = basePath;
        return (T) this;
    }

    public T openApiConfiguration(OpenApiConfiguration openApiConfiguration) {
        this.openApiConfiguration = openApiConfiguration;
        return (T) this;
    }

    public void setOpenApiConfiguration(OpenApiConfiguration openApiConfiguration) {
        this.openApiConfiguration = openApiConfiguration;
    }

    public String getConfigLocation() {
        return configLocation;
    }

    public void setConfigLocation(String configLocation) {
        this.configLocation = configLocation;
    }

    protected String configLocation;

    public final T configLocation(String configLocation) {
        this.configLocation = configLocation;
        return (T) this;
    }

    public void setOpenApiProcessors(Map<String, OpenApiProcessor> openApiProcessors) {
        this.openApiProcessors = openApiProcessors;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return this.id;
    }

    public final T id(String id) {
        this.id = id;
        return (T) this;
    }

    @Override
    public Map<String, OpenApiProcessor> getOpenApiProcessors() {
        return openApiProcessors;
    }

    @Override
    public OpenApiProcessor getDefaultProcessor() {
        if (openApiProcessors.isEmpty()) {
            if (parent != null) {
                return parent.getDefaultProcessor();
            }
            return null;
        }
        return openApiProcessors.get(id);
    }


    public void setParent(OpenApiContext parent) {
        this.parent = parent;
    }

    @Override
    public OpenApiContext getParent() {
        return this.parent;
    }

    public final T parent(OpenApiContext parent) {
        this.parent = parent;
        return (T) this;
    }


    public GenericOpenApiContext addOpenApiProcessor(OpenApiProcessor openApiProcessor) {
        if (StringUtils.isEmpty(openApiProcessor.getId())) {
            openApiProcessor.getOpenApiConfiguration().getOpenAPI().setInfo(
                    (openApiProcessor.getOpenApiConfiguration().getOpenAPI().getInfo() == null ?
                            new Info() :
                            openApiProcessor.getOpenApiConfiguration().getOpenAPI().getInfo()).title(id)
            );
        }
        openApiProcessors.put(openApiProcessor.getId(), openApiProcessor);
        return this;
    }

    protected OpenApiProcessor buildProcessor(String id, final OpenApiConfiguration openApiConfiguration) throws Exception {
        OpenApiProcessor processor;
        if (StringUtils.isNotBlank(openApiConfiguration.getProcessorClassName())) {
            Class cls = getClass().getClassLoader().loadClass(openApiConfiguration.getProcessorClassName());
            processor = (OpenApiProcessor) cls.newInstance();
        } else {
            processor = new GenericOpenApiProcessor().id(id).openApiConfiguration(openApiConfiguration);
        }

        // TODO remove, set by processor
        processor.setOpenApiScanner(buildScanner(openApiConfiguration));
        processor.setOpenApiReader(buildReader(openApiConfiguration));
        return processor;
    }



    // TODO implement in subclass, also handle classpath
    protected URL buildConfigLocationURL(String configLocation) {


        // TODO CLASSPATH, file path..
        //ServletContext.getResource()
        String sanitize = (configLocation.startsWith("/") ? configLocation : "/" + configLocation);
        if (true) return this.getClass().getResource(sanitize);



        configLocation = "file://" + configLocation;
        try {
            return new File(configLocation).toURL();
            //return new URL(configLocation);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    protected URL locateConfig() {
        if (StringUtils.isNotEmpty(configLocation)) {
            return buildConfigLocationURL(configLocation);
        }
        // TODO check known location in classpath, or same dir or whatever..
        return null;
    }

    @Override
    public OpenApiContext init() {

        URL configUrl = locateConfig();
        if (configUrl != null) {
            // TODO handle urls and stuff, also use loadConfiguration protected now in WebXmlContext..
            Map<String, OpenApiConfiguration> configurations =
                    new LocationOpenApiConfigBuilder()
                    .configLocation(locateConfig())
                    .buildMultiple(id);
            //Map<String, OpenApiConfiguration> configurations = OpenApiConfiguration.fromUrl(locateConfig(), id);
            for (String id : configurations.keySet()) {
                try {
                    openApiProcessors.put(id, buildProcessor(id, configurations.get(id)));
                } catch (Exception e) {
                    // TODO
                    e.printStackTrace();
                }

            }
        }

        // TODO here try with openApiBuilder? and replace OpenApiConfiguration.fromUrl

        if (openApiProcessors.isEmpty() && parent == null) {
            try {

                if (openApiConfiguration == null) {
                    openApiConfiguration = new OpenApiConfiguration().resourcePackageNames(resourcePackageNames);
                    openApiConfiguration.setId(id);
                }

                openApiProcessors.put(id, buildProcessor(id, openApiConfiguration));
            } catch (Exception e) {
                // TODO
                e.printStackTrace();
            }
        }
        for (OpenApiProcessor p : openApiProcessors.values()) {
            p.init();
        }
        register();
        return this;
    }

    protected void register() {
        OpenApiContextLocator.getInstance().putOpenApiContext(id, this);
    }

    @Override
    public OpenAPI read() {
        if (openApiProcessors.isEmpty()) {
            if (parent != null) {
                return parent.read();
            }
            return null;
        }
        return getDefaultProcessor().read();
    }

    @Override
    public OpenApiConfiguration getOpenApiConfiguration() {
        if (openApiConfiguration != null) {
            return openApiConfiguration;
        }
        if (!openApiProcessors.isEmpty()) {
            if (openApiProcessors.get(id) != null) {
                return openApiProcessors.get(id).getOpenApiConfiguration();
            }
        }
        // try parent
        if (parent != null) {
            return parent.getOpenApiConfiguration();
        }
        return null;
    }

    protected OpenApiReader buildReader(final OpenApiConfiguration openApiConfiguration) throws Exception {
        OpenApiReader reader;
        if (StringUtils.isNotBlank(openApiConfiguration.getReaderClassName())) {
            Class cls = getClass().getClassLoader().loadClass(openApiConfiguration.getReaderClassName());
            // TODO instantiate with configuration
            reader = (OpenApiReader) cls.newInstance();
        } else {
            reader = new OpenApiReader() {
                @Override
                public OpenAPI read(Set<Class<?>> classes, Map<String, Object> resources) {
                    OpenAPI openApi = openApiConfiguration.getOpenAPI();
                    return openApi;

                }
            };
        }
        return reader;
    }

    protected OpenApiScanner buildScanner(final OpenApiConfiguration openApiConfiguration) throws Exception {
        OpenApiScanner scanner;
        if (StringUtils.isNotBlank(openApiConfiguration.getScannerClassName())) {
            Class cls = getClass().getClassLoader().loadClass(openApiConfiguration.getScannerClassName());
            // TODO instantiate with configuration
            scanner = (OpenApiScanner) cls.newInstance();
        } else {
            scanner = new GenericOpenApiScanner(openApiConfiguration);
        }
        return scanner;
    }

}
