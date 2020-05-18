package com.gnanadurai.sftp;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.UserAuth;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.password.UserAuthPasswordFactory;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.scp.ScpCommandFactory;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.subsystem.sftp.SftpSubsystemFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

public class SftpApplicationTests {

	private static final Logger LOGGER = LoggerFactory.getLogger(SftpApplicationTests.class);

	private SshServer sshServer;
	private static final int PORT = 22999;

	File TEST = new File("E:\\SSH\\test");
	File ADMIN = new File("E:\\SSH\\admin");
	File ERROR = new File("E:\\SSH\\error");

	@Before
	public void setup() {
		TEST.mkdirs();
		ADMIN.mkdirs();
		ERROR.mkdirs();

		sshServer = SshServer.setUpDefaultServer();
		sshServer.setFileSystemFactory(new VirtualFileSystemFactory(Paths.get(ERROR.getAbsolutePath())));
		sshServer.setPort(PORT);
		sshServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(new File("my.pem")));
		sshServer.setCommandFactory(new ScpCommandFactory());
		List<NamedFactory<UserAuth>> userAuthFactories = new ArrayList<>();
		userAuthFactories.add(new UserAuthPasswordFactory());
		sshServer.setUserAuthFactories(userAuthFactories);
		sshServer.setPasswordAuthenticator(new PasswordAuthenticator() {
			@Override
			public boolean authenticate(String username, String password, ServerSession session) {
				if ((username.equals("test")) && (password.equals("test"))) {
					sshServer.setFileSystemFactory(new VirtualFileSystemFactory(Paths.get(TEST.getAbsolutePath())));
					return true;
				}
				if ((username.equals("admin")) && (password.equals("admin"))) {
					sshServer.setFileSystemFactory(new VirtualFileSystemFactory(Paths.get(ADMIN.getAbsolutePath())));
					return true;
				}
				return false;
			}
		});
		List<NamedFactory<Command>> namedFactoryList = new ArrayList<>();
		namedFactoryList.add(new SftpSubsystemFactory());
		sshServer.setSubsystemFactories(namedFactoryList);
		try {
			sshServer.start();
		} catch (IOException ex) {
			LOGGER.error(ex.getMessage(), ex);
		}
	}

	@After
	public void teardown() throws Exception {
		sshServer.stop();
	}

	@Test
	public void testPutAndGetFile() {
		try {
			JSch jsch = new JSch();
			Hashtable<String, String> config = new Hashtable<>();
			config.put("StrictHostKeyChecking", "no");
			JSch.setConfig(config);

			Session session = jsch.getSession("test", "localhost", PORT);
			session.setPassword("test");
			session.connect();

			LOGGER.info("Session Connected: " + session.isConnected());

			ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
			sftpChannel.connect();

			final String testFileContents = "Sample file contents";
			String uploadedFileName = "uploadFile";
			sftpChannel.put(new ByteArrayInputStream(testFileContents.getBytes()), uploadedFileName);

			String downloadedFileName = "downLoadFile";
			sftpChannel.get(uploadedFileName, downloadedFileName);

			File downloadedFile = new File(downloadedFileName);

			assertTrue(downloadedFile.exists());

			if (sftpChannel.isConnected()) {
				sftpChannel.exit();
				LOGGER.info("Disconnected channel.");
			}
			if (session.isConnected()) {
				session.disconnect();
				LOGGER.info("Disconnected session.");
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

}
