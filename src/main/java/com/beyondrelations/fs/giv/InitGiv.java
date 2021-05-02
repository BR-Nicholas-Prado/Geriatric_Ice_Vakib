
package com.beyondrelations.fs.giv;

import java.nio.file.FileSystems;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.ProviderNotFoundException;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command( version = "Geriatric-Ice-Vakib 1.0" )
public class InitGiv
		implements Runnable
{

	public static void main(String[] args)
	{
		System.exit( new CommandLine( new InitGiv() ).execute( args ) );
	}


	@Option(
			names = { "-v", "--verbose" },
			description = "Verbose mode. Helpful for troubleshooting.")
    private boolean verbose;

	@Parameters(
			index = "0",
			defaultValue = "",
			paramLabel = "FILE",
			description = "Directory to process.")
    private String workingRoot;


	@Option(
			names = { "-h", "--help" },
			usageHelp = true,
			description = "Explain arguments.")
    private boolean picocliHandlesHelp;


	@Option(
			names = { "-V", "--Verbose" },
			versionHelp = true,
			description = "Explain arguments.")
    private boolean picocliHandlesVersion;


	public void run()
	{
		try {
			Givakib giv = new Givakib( FileSystems.getDefault() );
			giv.setVerbose( verbose );
			giv.replaceJarsWithTombstonesIn( workingRoot );
		}
		catch ( IllegalArgumentException | SecurityException
				| FileSystemNotFoundException | ProviderNotFoundException any )
		{
			any.printStackTrace();
		}
	}

	
}























