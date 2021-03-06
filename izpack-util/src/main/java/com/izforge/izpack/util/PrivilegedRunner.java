/*
 * IzPack - Copyright 2001-2008 Julien Ponge, All Rights Reserved.
 *
 * http://izpack.org/
 * http://izpack.codehaus.org/
 *
 * Copyright 2008 Julien Ponge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.izforge.izpack.util;

import static com.izforge.izpack.util.Platform.Name.MAC_OSX;
import static com.izforge.izpack.util.Platform.Name.UNIX;
import static com.izforge.izpack.util.Platform.Name.WINDOWS;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is responsible for allowing the installer to re-launch itself with administrator permissions.
 * The way of achieving this greatly varies among the platforms. The JDK classes are of not help here as there
 * is no way to tell a JVM to run as a different user but to launch a new one.
 *
 * @author Julien Ponge
 */
public class PrivilegedRunner
{

    /**
     * The current platform.
     */
    private final Platform platform;

    /**
     * The logger.
     */
    private static final Logger logger = Logger.getLogger(PrivilegedRunner.class.getName());


    /**
     * Builds a default privileged runner.
     *
     * @param platform the current platform
     */
    public PrivilegedRunner(Platform platform)
    {
        this.platform = platform;
    }

    /**
     * Checks if the current platform is supported.
     *
     * @return <code>true</code> if the platform is supported, <code>false</code> otherwise.
     */
    public boolean isPlatformSupported()
    {
        return platform.isA(UNIX) || platform.isA(WINDOWS);
    }

    /**
     * Determines if elevated rights are required to install/uninstall the application.
     *
     * @return <code>true</code> if elevation is needed to have administrator permissions, <code>false</code> otherwise.
     */
    public boolean isElevationNeeded()
    {
        return isElevationNeeded(null);
    }

    /**
     * Determines if elevated rights are required to install/uninstall the application.
     *
     * @param path the installation path, or <tt>null</tt> if the installation path is unknown
     * @return <tt>true</tt> if elevation is needed to have administrator permissions, <tt>false</tt> otherwise.
     */
    public boolean isElevationNeeded(String path)
    {
        boolean result;
        if (platform.isA(WINDOWS))
        {
            if (path == null || path.trim().length() == 0)
            {
                path = getProgramFiles();
            }
            result = !isPrivilegedMode() && !canWrite(path);
        }
        else
        {
            if (path != null)
            {
                result = !canWrite(path);
            }
            else
            {
                result = !System.getProperty("user.name").equals("root");
            }
        }
        return result;
    }

    /**
     * Relaunches the installer with elevated rights.
     *
     * @return the status code returned by the launched process (by convention, 0 means a success).
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the launch was interrupted
     */
    public int relaunchWithElevatedRights() throws IOException, InterruptedException
    {
        String javaCommand = getJavaCommand();
        String installer = getInstallerJar();
        ProcessBuilder builder = new ProcessBuilder(getElevator(javaCommand, installer));
        builder.environment().put("izpack.mode", "privileged");
        return builder.start().waitFor();
    }

    public static boolean isPrivilegedMode()
    {
        return "privileged".equals(System.getenv("izpack.mode")) || "privileged".equals(
                System.getProperty("izpack.mode"));
    }

    protected List<String> getElevator(String javaCommand, String installer) throws IOException
    {
        List<String> elevator = new ArrayList<String>();

        if (platform.isA(MAC_OSX))
        {
            elevator.add(extractMacElevator().getCanonicalPath());
            elevator.add(javaCommand);
            elevator.add("-jar");
            elevator.add(installer);
        }
        else if (platform.isA(UNIX))
        {
            elevator.add("xterm");
            elevator.add("-title");
            elevator.add("Installer");
            elevator.add("-e");
            elevator.add("sudo");
            elevator.add(javaCommand);
            elevator.add("-jar");
            elevator.add(installer);
        }
        else if (platform.isA(WINDOWS))
        {
            elevator.add("wscript");
            elevator.add(extractVistaElevator().getCanonicalPath());
            elevator.add(javaCommand);
            elevator.add("-Dizpack.mode=privileged");
            elevator.add("-jar");
            elevator.add(installer);
        }

        return elevator;
    }

    protected File extractVistaElevator() throws IOException
    {
        String path = System.getProperty("java.io.tmpdir") + File.separator + "Installer.js";
        File elevator = new File(path);

        FileOutputStream out = new FileOutputStream(elevator);
        InputStream in = getClass().getResourceAsStream("/com/izforge/izpack/util/windows/elevate.js");
        copyStream(out, in);
        in.close();
        out.close();

        elevator.deleteOnExit();
        return elevator;
    }

    protected File extractMacElevator() throws IOException
    {
        String path = System.getProperty("java.io.tmpdir") + File.separator + "Installer";
        File elevator = new File(path);

        FileOutputStream out = new FileOutputStream(elevator);
        InputStream in = getClass().getResourceAsStream("/com/izforge/izpack/util/mac/run-with-privileges-on-osx");
        copyStream(out, in);
        in.close();
        out.close();

        if (!elevator.setExecutable(true))
        {
            throw new IOException("Failed to set execute permission on " + path);
        }

        elevator.deleteOnExit();
        return elevator;
    }

    private void copyStream(OutputStream out, InputStream in) throws IOException
    {
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) >= 0)
        {
            out.write(buffer, 0, bytesRead);
        }
    }

    private String getInstallerJar()
    {
        try
        {
            URI uri = getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
            if (!"file".equals(uri.getScheme()))
            {
                throw new Exception("Unexpected scheme in JAR file URI: " + uri);
            }
            return new File(uri.getSchemeSpecificPart()).getCanonicalPath();
        }
        catch (Exception e)
        {
            logger.log(Level.INFO, e.getMessage(), e);
        }
        return null;
    }

    private String getJavaCommand()
    {
        return System.getProperty("java.home") + File.separator + "bin" + File.separator + getJavaExecutable();
    }

    private String getJavaExecutable()
    {
        if (OsVersion.IS_WINDOWS)
        {
            return "javaw.exe";
        }
        else
        {
            return "java";
        }
    }

    /**
     * Determines if the specified path can be written to.
     *
     * @param path the path to check
     * @return <tt>true</tt> if the path can be written to, otherwise <tt>false</tt>
     */
    private boolean canWrite(String path)
    {
        File file = new File(path);
        boolean canWrite = file.canWrite();
        if (canWrite)
        {
            // make sure that the path can actually be written to, for IZPACK-727
            try
            {
                File test = File.createTempFile(".izpackwritecheck", null, file);
                if (!test.delete())
                {
                    test.deleteOnExit();
                }
            }
            catch (IOException exception)
            {
                canWrite = false;
            }
        }
        return canWrite;
    }

    /**
     * Tries to determine the Windows Program Files directory.
     *
     * @return the Windows Program Files directory
     */
    private String getProgramFiles()
    {
        String path = System.getenv("ProgramFiles");
        if (path == null)
        {
            path = "C:\\Program Files";
        }
        return path;
    }

}
