package nebula.test.functional.internal.classpath;

import nebula.test.functional.internal.GradleHandle;
import nebula.test.functional.internal.GradleHandleFactory;
import org.gradle.api.Transformer;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ClasspathInjectingGradleHandleFactory implements GradleHandleFactory {

    private final ClassLoader classLoader;
    private final GradleHandleFactory delegateFactory;

    public ClasspathInjectingGradleHandleFactory(ClassLoader classLoader, GradleHandleFactory delegateFactory) {
        this.classLoader = classLoader;
        this.delegateFactory = delegateFactory;
    }

    public GradleHandle start(File directory, List<String> arguments) {
        File testKitDir = new File(directory, ".gradle-test-kit");
        if (!testKitDir.exists()) {
            GFileUtils.mkdirs(testKitDir);
        }

        File initScript = new File(testKitDir, "init.gradle");
        new ClasspathAddingInitScriptBuilder().build(initScript, classLoader);

        List<String> ammendedArguments = new ArrayList<String>(arguments.size() + 2);
        ammendedArguments.add("-I");
        ammendedArguments.add(initScript.getAbsolutePath());
        ammendedArguments.addAll(arguments);
        return delegateFactory.start(directory, ammendedArguments);
    }
}
