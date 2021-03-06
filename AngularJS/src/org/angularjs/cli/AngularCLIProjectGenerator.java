package org.angularjs.cli;

import com.intellij.execution.filters.Filter;
import com.intellij.ide.util.projectWizard.ModuleNameLocationSettings;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.javascript.nodejs.util.NodePackage;
import com.intellij.lang.javascript.boilerplate.NpmPackageProjectGenerator;
import com.intellij.lang.javascript.boilerplate.NpxPackageDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TextAccessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import com.intellij.xml.util.XmlStringUtil;
import icons.AngularJSIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * @author Dennis.Ushakov
 */
public class AngularCLIProjectGenerator extends NpmPackageProjectGenerator {

  public static final String PACKAGE_NAME = "@angular/cli";
  private static final Pattern VALID_NG_APP_NAME = Pattern.compile("[a-zA-Z][0-9a-zA-Z]*(-[a-zA-Z][0-9a-zA-Z]*)*");

  @Nls
  @NotNull
  @Override
  public String getName() {
    return "Angular CLI";
  }

  @Override
  @NotNull
  public String getDescription() {
    return "The Angular CLI makes it easy to create an application that already works, right out of the box. It already follows our best practices!";
  }

  @Override
  @NotNull
  public Icon getIcon() {
    return AngularJSIcons.Angular2;
  }

  @Override
  protected void customizeModule(@NotNull VirtualFile baseDir, ContentEntry entry) {
    if (entry != null) {
      AngularJSProjectConfigurator.excludeDefault(baseDir, entry);
    }
  }

  @Override
  @NotNull
  protected String[] generatorArgs(@NotNull Project project, @NotNull VirtualFile baseDir) {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  @NotNull
  protected String[] generatorArgs(@NotNull Project project, @NotNull VirtualFile baseDir, @NotNull Settings settings) {
    return new String[]{"new", baseDir.getName()};
  }

  @NotNull
  @Override
  protected Filter[] filters(@NotNull Project project, @NotNull VirtualFile baseDir) {
    return new Filter[]{new AngularCLIFilter(project, baseDir.getParent().getPath())};
  }

  @NotNull
  @Override
  protected String executable(@NotNull NodePackage pkg) {
    return ng(pkg.getSystemDependentPath());
  }

  @NotNull
  public static String ng(String path) {
    return path + File.separator + "bin" + File.separator + "ng";
  }

  @Override
  @NotNull
  protected String packageName() {
    return PACKAGE_NAME;
  }

  @Override
  @NotNull
  protected String presentablePackageName() {
    return "Angular &CLI:";
  }

  @NotNull
  @Override
  protected List<NpxPackageDescriptor.NpxCommand> getNpxCommands() {
    return Collections.singletonList(new NpxPackageDescriptor.NpxCommand(PACKAGE_NAME, "ng"));
  }

  @Override
  protected String validateProjectPath(@NotNull String path) {
    return Optional.ofNullable(validateFolderName(path, "Project"))
                   .orElseGet(() -> super.validateProjectPath(path));
  }

  @SuppressWarnings("deprecation")
  @NotNull
  @Override
  public GeneratorPeer<Settings> createPeer() {
    return new AngularCLIProjectGeneratorPeer();
  }

  @NotNull
  @Override
  protected File workingDir(Settings settings, @NotNull VirtualFile baseDir) {
    return VfsUtilCore.virtualToIoFile(baseDir).getParentFile();
  }


  @NotNull
  @Override
  protected Runnable postInstall(@NotNull Project project,
                                 @NotNull VirtualFile baseDir,
                                 File workingDir) {
    return () -> ApplicationManager.getApplication().executeOnPooledThread(() -> {
      super.postInstall(project, baseDir, workingDir).run();
      AngularCliUtil.createRunConfigurations(project, baseDir);
    });
  }

  @Nullable
  private static String validateFolderName(String path, String label) {
    String fileName = PathUtil.getFileName(path);
    if (!VALID_NG_APP_NAME.matcher(fileName).matches()) {
      return XmlStringUtil.wrapInHtml(
        label + " name '" + fileName + "' is not valid. " + label + " name must " +
        "start with a letter, and must contain only alphanumeric characters or dashes. " +
        "When adding a dash the segment after the dash must also start with a letter."
      );
    }
    return null;
  }


  private class AngularCLIProjectGeneratorPeer extends NpmPackageGeneratorPeer {

    private TextAccessor myContentRoot;

    @Override
    public void buildUI(@NotNull SettingsStep settingsStep) {
      super.buildUI(settingsStep);
      final ModuleNameLocationSettings field = settingsStep.getModuleNameLocationSettings();
      if (field != null) {
        myContentRoot = new TextAccessor() {
          @Override
          public void setText(@NotNull String text) {
            field.setModuleContentRoot(text);
          }

          @Override
          @NotNull
          public String getText() {
            return field.getModuleContentRoot();
          }
        };
      }
    }

    @Nullable
    @Override
    public ValidationInfo validate() {
      final ValidationInfo info = super.validate();
      if (info != null) {
        return info;
      }
      if (myContentRoot != null) {
        String message = validateFolderName(myContentRoot.getText(), "Content root folder");
        if (message != null) {
          return new ValidationInfo(message);
        }
      }
      return null;
    }
  }
}
