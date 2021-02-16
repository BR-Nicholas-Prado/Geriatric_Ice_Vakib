
package com.beyondrelations.fs.giv;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderNotFoundException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class Givakib
{

	private FileSystem os;


	public static void main(
			String[] args
	) {
		try {
			Givakib giv = new Givakib( FileSystems.getDefault() );
			giv.replaceJarsWithTombstonesIn( args.length > 0
					? args[ 0 ] : "" );
		}
		catch ( IllegalArgumentException | SecurityException
				| FileSystemNotFoundException | ProviderNotFoundException any )
		{
			any.printStackTrace();
		}
	}


	public Givakib(
			FileSystem ls
	) {
		if ( ls == null )
			throw new RuntimeException( "null filesystem is null" );
		os = ls;
	}


	public void replaceJarsWithTombstonesIn(
			String descriptionOfPath
	) {
		if ( descriptionOfPath.isEmpty() )
			descriptionOfPath = ".";
		try {
			tombstonesInImmediateChildren( os.getPath( descriptionOfPath ) );
		}
		catch ( InvalidPathException ipe )
		{
			ipe.printStackTrace();
			return;
		}
	}


	private void tombstonesInImmediateChildren(
			Path workingRoot
	) {
		try
		{
			Stream<Path> allContents = Files.list( workingRoot );
			Object[] allFilesInRoot = allContents.toArray();
			Arrays.sort( allFilesInRoot,
					// comparator
					( left, right ) -> {
						Path leftP = (Path)left;
						Path rightP = (Path)right;
						String leftPath = leftP.getFileName().toString();
						String rightPath = rightP.getFileName().toString();
						if ( ! leftPath.contains( "auto" ) )
							// IMPROVE || ! leftPath.contains( "RELEASE" ) ) or unused or 0300 or 0200 or 0201
							return leftPath.compareTo( rightPath );
						// else treat as our format, for numeric, not lexical sorting
						String[] leftPieces = leftPath.split( "-" );
						String[] rightPieces = rightPath.split( "-" );
						final int versionInd = 2;
						int leftVersion = Integer.parseInt( leftPieces[ versionInd ] );
						int rightVersion = Integer.parseInt( rightPieces[ versionInd ] );
						if ( leftVersion == rightVersion )
							return 0;
						else if ( leftVersion < rightVersion )
							return -1;
						else
							return 1;
				} );
			Sculptor carver = new Sculptor();
			int ind = 0, lim = allFilesInRoot.length -1; // leave the last one alone
			for ( Object buh : allFilesInRoot )
			{
				if ( ind < lim )
					ind++;
				else
					break;
				Path path = (Path)buh;
				if ( Files.isDirectory( path ) )
				{
		System.out.println( "entering "+ path.toString() );
					replaceJarsWithTombstonesIn( path, carver );
				}
			}
		}
		catch ( IOException ie )
		{
			ie.printStackTrace();
			return;
		}
	}


	private void replaceJarsWithTombstonesIn(
			Path dirWithJarFiles, Sculptor replacesJars
	) {
		try
		{
			Stream<Path> allContents = Files.list( dirWithJarFiles );
			allContents.forEach( replacesJars );
		}
		catch ( IOException ie )
		{
			ie.printStackTrace();
			return;
		}
	}


	/** because it carves tombstones */
	private class Sculptor
			implements Consumer<Path>
	{

		public void accept(
				Path target
		) {
		System.out.println( "handed "+ target.toString() );
			if ( ! target.getFileName().toString().endsWith( "jar" ) )
				return;
		System.out.println( "using it" );
			try {
				FileTime creationTime = (FileTime)Files.getAttribute( target, "basic:creationTime" );
				FileTime modifiedTime = (FileTime)Files.getAttribute( target, "basic:lastModifiedTime" );
				// improve consider comparing creation with modified, in case I copied this in
				LocalDate createdD = creationTime.toInstant().atZone( ZoneId.systemDefault() ).toLocalDate();
				LocalDate modifiedD = modifiedTime.toInstant().atZone( ZoneId.systemDefault() ).toLocalDate();
				LocalDate earlierD = createdD.compareTo( modifiedD ) < 1 ? createdD : modifiedD;
				String tombstoneName = earlierD.toString() +"_"+ target.getFileName().toString()
						.replace( "jar", "txt" );
		System.out.println( "creating "+ tombstoneName );
					Path intendedTombstone = target.getParent().resolve( os.getPath( tombstoneName ) );
		System.out.println( "position "+ intendedTombstone.toString() );
				Path ignored = Files.createFile( intendedTombstone );
				Files.delete( target );
			}
			catch ( UnsupportedOperationException
					| SecurityException | IOException | InvalidPathException ie )
			{
				ie.printStackTrace();
				return;
			}
		}

	}


}
























