/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.wtp;

import java.io.IOException;
import java.io.InputStream;

import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jst.j2ee.internal.J2EEVersionConstants;
import org.eclipse.jst.j2ee.web.project.facet.WebFacetUtils;
import org.eclipse.jst.jee.util.internal.JavaEEQuickPeek;
import org.eclipse.wst.common.project.facet.core.IProjectFacetVersion;
import org.maven.ide.eclipse.wtp.internal.StringUtils;


/**
 * See http://maven.apache.org/plugins/maven-war-plugin/war-mojo.html
 * 
 * @author Igor Fedorenko
 */
@SuppressWarnings("restriction")
class WarPluginConfiguration {
  private static final String WAR_SOURCE_FOLDER = "/src/main/webapp";

  private static final String WAR_PACKAGING = "war";

  private static final String WEB_XML = "WEB-INF/web.xml";

  private static final int WEB_3_0_ID = 30;//Same Value as J2EEVersionConstants.WEB_3_0_ID from WTP 3.2 (org.eclipse.jst.j2ee.core_1.2.0.vX.jar)

  final Plugin plugin;

  private IProject project;

  public WarPluginConfiguration(MavenProject mavenProject, IProject project) {
    this.plugin = mavenProject.getPlugin("org.apache.maven.plugins:maven-war-plugin");
    this.project = project;
  }

  static boolean isWarProject(MavenProject mavenProject) {
    return WAR_PACKAGING.equals(mavenProject.getPackaging());
  }


  /**
   * @return war plugin configuration or null.
   */
  private Xpp3Dom getConfiguration() {
    if(plugin == null) {
      return null;
    }
    return (Xpp3Dom) plugin.getConfiguration();
  }

  public Xpp3Dom[] getWebResources() {
    Xpp3Dom config = getConfiguration();
    if(config != null) {
      Xpp3Dom[] children = config.getChildren("webResources");
      return children;
    }
    return null;
  }

  public String getValueForWebResource(Xpp3Dom dom, String value) {
    Xpp3Dom resource = dom.getChild("resource");
    if(resource != null) {
      Xpp3Dom child = resource.getChild(value);
      if(child != null) {
        return child.getValue();
      }
    }
    return null;
  }

  public String getDirectoryForWebResource(Xpp3Dom dom) {
    return getValueForWebResource(dom, "directory");
  }

  public String getTargetPathForWebResource(Xpp3Dom dom) {
    return getValueForWebResource(dom, "targetPath");
  }

  public String getWarSourceDirectory() {
    Xpp3Dom dom = getConfiguration();
    if(dom == null) {
      return WAR_SOURCE_FOLDER;
    }

    Xpp3Dom[] warSourceDirectory = dom.getChildren("warSourceDirectory");
    if(warSourceDirectory != null && warSourceDirectory.length > 0) {
      // first one wins
      String dir = warSourceDirectory[0].getValue();
      //MNGECLIPSE-1600 fixed absolute warSourceDirectory thanks to Snjezana Peco's patch
      if(project != null) {
        IPath projectLocationPath = project.getLocation();
        if(projectLocationPath != null && dir != null) {
          String projectLocation = projectLocationPath.toOSString();
          if(dir.startsWith(projectLocation)) {
            return dir.substring(projectLocation.length());
          }
        }
      }
      return dir;
    }

    return WAR_SOURCE_FOLDER;
  }

  public String[] getPackagingExcludes() {
    Xpp3Dom config = getConfiguration();
    if(config != null) {
        Xpp3Dom excl = config.getChild("packagingExcludes");
        if(excl != null) {
          return StringUtils.tokenizeToStringArray(excl.getValue(), ",");
        }
    }
    return new String[0];
  }

  public String[] getPackagingIncludes() {
    Xpp3Dom config = getConfiguration();
    if(config != null) {
      Xpp3Dom incl = config.getChild("packagingIncludes");
      if(incl != null && incl.getValue() != null) {
        return StringUtils.tokenizeToStringArray(incl.getValue(), ",");
      }
    }
    return new String[0];
  }

  public boolean isAddManifestClasspath() {
    Xpp3Dom config = getConfiguration();
    if(config != null) {
      Xpp3Dom arch = config.getChild("archive");
      if(arch != null) {
        Xpp3Dom manifest = arch.getChild("manifest");
        if(manifest != null) {
          Xpp3Dom addToClp = manifest.getChild("addClasspath");
          if(addToClp != null) {
            return Boolean.valueOf(addToClp.getValue());
          }
        }
      }
  }
    return false;
  }

  public String getManifestClasspathPrefix() {
    Xpp3Dom config = getConfiguration();
    if(config != null) {
      Xpp3Dom arch = config.getChild("archive");
      if(arch != null) {
        Xpp3Dom manifest = arch.getChild("manifest");
        if(manifest != null) {
          Xpp3Dom prefix = manifest.getChild("classpathPrefix");
          if(prefix != null) {
            return prefix.getValue();
          }
        }
      }
    }
    return null;
  }

  public IProjectFacetVersion getWebFacetVersion(IProject project) {
    IFile webXml;
    String customWebXml = getCustomWebXml(project);
    if (customWebXml == null) {
      webXml = project.getFolder(getWarSourceDirectory()).getFile(WEB_XML);
    } else {
      webXml = project.getFile(customWebXml);
    }

    if(webXml.isAccessible()) {
      try {
        InputStream is = webXml.getContents();
        try {
          JavaEEQuickPeek jqp = new JavaEEQuickPeek(is);
          switch(jqp.getVersion()) {
            case J2EEVersionConstants.WEB_2_2_ID:
              return WebFacetUtils.WEB_22;
            case J2EEVersionConstants.WEB_2_3_ID:
              return WebFacetUtils.WEB_23;
            case J2EEVersionConstants.WEB_2_4_ID:
              return WebFacetUtils.WEB_24;
            case J2EEVersionConstants.WEB_2_5_ID:
              return WebFacetUtils.WEB_FACET.getVersion("2.5");
            //MNGECLIPSE-1978  
            case WEB_3_0_ID://JavaEEQuickPeek will return this value only if WTP version >= 3.2
              return WebFacetUtils.WEB_FACET.getVersion("3.0");//only exists in WTP version >= 3.2
          }
        } finally {
          is.close();
        }
      } catch(IOException ex) {
        // expected
      } catch(CoreException ex) {
        // expected
      }
    }
   
    //MNGECLIPSE-1978 If no web.xml found and the project depends on some java EE 6 jar and WTP >= 3.2, then set web facet to 3.0
    if (WTPProjectsUtil.isJavaEE6Available() && WTPProjectsUtil.hasInClassPath(project, "javax.servlet.annotation.WebServlet")) {
      return WebFacetUtils.WEB_FACET.getVersion("3.0");
    }
    
    //MNGECLIPSE-984 web.xml is optional for 2.5 Web Projects
    return WTPProjectsUtil.DEFAULT_WEB_FACET;
    //We don't want to prevent the project creation when the java compiler level is < 5, we coud try that instead :
    //IProjectFacetVersion javaFv = JavaFacetUtils.compilerLevelToFacet(JavaFacetUtils.getCompilerLevel(project));
    //return (JavaFacetUtils.JAVA_50.compareTo(javaFv) > 0)?WebFacetUtils.WEB_24:WebFacetUtils.WEB_25; 
  }
  /**
   * Get the custom location of web.xml, as set in &lt;webXml&gt;.
   * @return the custom location of web.xml or null if &lt;webXml&gt; is not set
   */
  public String getCustomWebXml(IProject project) {
    Xpp3Dom config = getConfiguration();
    if(config != null) {
      Xpp3Dom webXmlDom = config.getChild("webXml");
      if(webXmlDom != null && webXmlDom.getValue() != null) {
        String webXmlFile = webXmlDom.getValue().trim();
        webXmlFile = ProjectUtils.getRelativePath(project, webXmlFile);
        return webXmlFile;
      }
    }
    return null;
  }


}
