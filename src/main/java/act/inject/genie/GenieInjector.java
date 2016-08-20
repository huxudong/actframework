package act.inject.genie;

import act.Act;
import act.app.App;
import act.app.event.AppEventId;
import act.controller.ActionMethodParamAnnotationHandler;
import act.inject.DependencyInjectionBinder;
import act.inject.DependencyInjectorBase;
import act.inject.ActProviders;
import act.util.SubClassFinder;
import org.osgl.$;
import org.osgl.Osgl;
import org.osgl.exception.NotAppliedException;
import org.osgl.inject.Genie;
import org.osgl.inject.InjectListener;
import org.osgl.inject.Module;
import org.osgl.inject.ScopeCache;
import org.osgl.inject.annotation.Provided;
import org.osgl.mvc.annotation.Bind;
import org.osgl.mvc.annotation.Param;
import org.osgl.util.C;
import org.osgl.util.E;

import javax.inject.Inject;
import javax.inject.Provider;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GenieInjector extends DependencyInjectorBase<GenieInjector> {

    private static final Module SCOPE_MODULE = new Module() {
        @Override
        protected void configure() {
            bind(ScopeCache.SessionScope.class).to(new SessionScope());
            bind(ScopeCache.RequestScope.class).to(new RequestScope());
            bind(ScopeCache.SingletonScope.class).to(new SingletonScope());
        }
    };

    private volatile Genie genie;
    private List<Object> modules;

    public GenieInjector(App app) {
        super(app);
        modules = factories().append(SCOPE_MODULE);
    }

    @Override
    public <T> T get(Class<T> clazz) {
        return genie().get(clazz);
    }

    @Override
    public <T> Provider<T> getProvider(Class<T> aClass) {
        return genie().getProvider(aClass);
    }

    @Override
    public synchronized void registerDiBinder(DependencyInjectionBinder binder) {
        super.registerDiBinder(binder);
        if (null != genie) {
            genie.registerProvider(binder.targetClass(), binder);
        }
    }

    @Override
    public boolean isProvided(Class<?> type) {
        return ActProviders.isProvided(type)
                || type.isAnnotationPresent(Provided.class)
                || type.isAnnotationPresent(Inject.class);
    }

    @Override
    public boolean isQualifier(Class<? extends Annotation> aClass) {
        return genie().isQualifier(aClass);
    }

    @Override
    public boolean isPostConstructProcessor(Class<? extends Annotation> aClass) {
        return genie().isPostConstructProcessor(aClass);
    }

    @Override
    public boolean isScope(Class<? extends Annotation> aClass) {
        return genie().isScope(aClass);
    }

    public void addModule(Object module) {
        E.illegalStateIf(null != genie);
        modules.add(module);
    }

    private C.List<Object> factories() {
        Set<String> factories = GenieFactoryFinder.factories();
        int len = factories.size();
        C.List<Object> list = C.newSizedList(factories.size());
        if (0 == len) {
            return list;
        }
        ClassLoader cl = App.instance().classLoader();
        for (String className : factories) {
            list.add($.classForName(className, cl));
        }
        return list;
    }

    private Genie genie() {
        if (null == genie) {
            synchronized (this) {
                if (null == genie) {
                    InjectListener listener = new GenieListener(this);
                    genie = Genie.create(listener, modules.toArray(new Object[modules.size()]));
                    for (Map.Entry<Class, DependencyInjectionBinder> entry : binders.entrySet()) {
                        genie.registerProvider(entry.getKey(), entry.getValue());
                    }
                    $.F2<Class, Provider, Void> register = new $.F2<Class, Provider, Void>() {
                        @Override
                        public Void apply(Class aClass, Provider provider) throws NotAppliedException, Osgl.Break {
                            genie.registerProvider(aClass, provider);
                            return null;
                        }
                    };
                    genie.registerQualifiers(Bind.class, Param.class);
                    List<ActionMethodParamAnnotationHandler> list = Act.pluginManager().pluginList(ActionMethodParamAnnotationHandler.class);
                    for (ActionMethodParamAnnotationHandler h : list) {
                        Set<Class<? extends Annotation>> set = h.listenTo();
                        for (Class<? extends Annotation> c: set) {
                            genie.registerQualifiers(c);
                        }
                    }

                    ActProviders.registerBuiltInProviders(ActProviders.class, register);
                    ActProviders.registerBuiltInProviders(GenieProviders.class, register);
                }
            }
        }
        return genie;
    }

    @SubClassFinder(value = Module.class, callOn = AppEventId.DEPENDENCY_INJECTOR_LOADED)
    public static void foundModule(Class<? extends Module> moduleClass) {
        App app = App.instance();
        GenieInjector genieInjector = app.injector();
        genieInjector.addModule($.newInstance(moduleClass));
    }

}
