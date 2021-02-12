
package com.beyondrelations.fs.giv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Givakib
{

	public static void main(
			String[] args
	) {
		/*
		get the working root, or assume right here
		
		*/
		try
		{
			Path workingRoot = Paths.get( "example" );
			Stream<Path> allContents = Files.list( workingRoot );
			Object[] allFilesInRoot = allContents.toArray();
			int ind = 0, lim = allFilesInRoot.length -1; // leave the last one alone
			for ( Object buh : allFilesInRoot )
			{
				if ( ind < lim )
					ind++;
				else
					break;
				Path path = (Path)buh;
				System.out.println( path.toString() );
			}
		}
		catch ( IOException ie )
		{
			ie.printStackTrace();
		}
	}

}
























