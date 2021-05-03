
package com.beyondrelations.fs.giv;

import java.io.File;
import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Givakib
{

	private final Logger log = LoggerFactory.getLogger( Givakib.class );
	private FileSystem os;
	private boolean verbose = false;

	private enum Command
	{
		HIDE_FOLDERS( "h" ),
		QUIT( "q" ),
		TOMBSTONE_FOLDERS( "t" ),
		UI_CHANGE_COLUMN_COUNT( "c" ),
		UI_CHANGE_SCREEN_CHAR_WIDTH( "w" ),
		UNKNOWN( "" );

		private String flag = "";

		private Command(
				String character
		) {
			flag = character;
		}
		public String getFlag(
		) {
			return flag;
		}
		public static Command fromFlag(
				String input
		) {
			if ( input == null || input.isEmpty() )
				return UNKNOWN;
			for ( Command candidate : values() )
				if ( candidate.flag.equals( input ) )
					return candidate;
			return UNKNOWN;
		}
	}

	private class UiResponse
	{
		Command command;
		Collection<Integer> values;

		UiResponse(
				Command doWhat, Collection<Integer> info
		) {
			command = doWhat;
			values = info;
		}
	}


	public Givakib(
			FileSystem ls
	) {
		if ( ls == null )
			throw new RuntimeException( "null filesystem is null" );
		os = ls;
	}


	public void interactivelyReplaceWithTombstonesIn(
			String descriptionOfPath
	) {
		final String here = "g.irwti "; // ask perhaps just let logger handle it
		if ( descriptionOfPath.isEmpty() )
			descriptionOfPath = ".";
		try {
			Set<Path> relevantFolders = immediateChildrenWithJar( os.getPath( descriptionOfPath ) );
			Map<Integer, Path> id_folder = new HashMap<>();
			int columns = 1;
			int screenCharacterWidth = 100;
			boolean askedToQuit = false;
			boolean rebuildMap = true;
			Scanner input = new Scanner( System.in );
			System.out.println( "[[ Command ]] [[ folder ids ]]\n:Options:" );
			for ( Command inputOption : Command.values() )
			{
				System.out.println( inputOption.getFlag() +" "+ inputOption.name() );
			}
			System.out.println();
			while ( ! askedToQuit )
			{
				if ( rebuildMap )
				{
					id_folder.clear();
					int ind = 1;
					Object[] sortable = relevantFolders.toArray();
					Arrays.sort( sortable );
					for ( Object someFolder : sortable )
						id_folder.put( Integer.valueOf( ind++ ), (Path)someFolder );
					rebuildMap = false;
				}
				renderFolderOptions( id_folder, columns, screenCharacterWidth );
				// ¶ get input
				UiResponse userChoice = null;
				int attempts = 10;
				while ( attempts > 0 )
				{
					System.out.print( " -- " );
					String literalInput = input.nextLine();
					if ( literalInput.isEmpty() )
						continue;
					userChoice = parsedInput( literalInput, id_folder );
					if ( userChoice == null )
						System.out.println( "That's not a valid choice, try another" );
					else if ( userChoice.command == Command.QUIT )
					{
						return;
					}
					else if ( satisfactorySelection( userChoice, screenCharacterWidth ) )
					{
						attempts = 10;
						break;
					}
					// ¶ satisfactorySelection() complained otherwise
					attempts--;
				}
				if ( attempts < 1 )
				{
					System.out.println( here +"ten abortive tries is enough to, quit" );
					askedToQuit = true;
					break;
				}
				else if ( userChoice.command == Command.UI_CHANGE_COLUMN_COUNT
						|| userChoice.command == Command.UI_CHANGE_SCREEN_CHAR_WIDTH )
				{
					Integer userColumns = userChoice.values.iterator().next();
					if ( userChoice.command == Command.UI_CHANGE_SCREEN_CHAR_WIDTH )
					{
						screenCharacterWidth = userColumns;
					}
					else if ( userChoice.command == Command.UI_CHANGE_COLUMN_COUNT )
					{
						columns = userColumns;
					}
				}
				else if ( userChoice.command == Command.HIDE_FOLDERS )
				{
					for ( Integer value : userChoice.values )
					{
						relevantFolders.remove( id_folder.get( value ) );
					}
					rebuildMap = true;
				}
				else if ( userChoice.command == Command.TOMBSTONE_FOLDERS )
				{
					Sculptor replacesJars = new Sculptor();
					for ( Integer value : userChoice.values )
					{
						if ( id_folder.containsKey( value ) )
						{
							Stream<Path> allContents = Files.list( id_folder.get( value ) );
							allContents.forEach( replacesJars );
							allContents.close();
							relevantFolders.remove( id_folder.get( value ) );
						}
					}
					rebuildMap = true;
				}
			}
		}
		catch ( IOException | InvalidPathException ipe )
		{
			ipe.printStackTrace();
			return;
		}
	}


	private void renderFolderOptions(
			Map<Integer, Path> id_folder, int columns, int screenCharacterWidth
	) {
		int evenlyDivisibleAmount = id_folder.size() / columns;
		int remainder = id_folder.size() - ( columns * evenlyDivisibleAmount );
		int[] maxIds = new int[ columns ], currentId = new int[ columns ];
		maxIds[ 0 ] = evenlyDivisibleAmount + remainder;
		// ¶ fill with the max of each column
		for ( int ind = 1; ind < columns; ind++ )
			maxIds[ ind ] = evenlyDivisibleAmount + maxIds[ ind -1 ] +1; // FIX too many, 43 for 41 because 41/2 = 21 apparently
		// ¶ fill with the current id of each column
		currentId[ 0 ] = 1;
		for ( int ind = 1; ind < maxIds.length; ind++ )
			currentId[ ind ] = maxIds[ ind -1 ] +1;
		// ¶ just using uniform columns, rather than minimal width for each
		int maxIdWidth = 1;
		if ( id_folder.size() > 10_000 )
			maxIdWidth = 5;
		else if ( id_folder.size() > 1_000 )
			maxIdWidth = 4;
		else if ( id_folder.size() > 100 )
			maxIdWidth = 3;
		else if ( id_folder.size() > 10 )
			maxIdWidth = 2;
		String formatForId = "%0"+ maxIdWidth +"d";
		int maxPathWidth = ( screenCharacterWidth / columns ) - maxIdWidth -3;
		String formatForWholeLine = " "+ formatForId +"  %-"+ maxPathWidth +"s"; // ¶ right pad the folder name
		for ( int rowInd = currentId[ 0 ]; rowInd < maxIds[ 0 ]; rowInd++ )
		{
			for ( int colInd = 0; colInd < maxIds.length; colInd++ )
			{
				if ( currentId[ colInd ] >= maxIds[ colInd ]
						|| ! id_folder.containsKey(  currentId[ colInd ] ) )
					continue;
				// ¶ not using log, so this isn't mixed in the same output
				System.out.print( String.format(
						formatForWholeLine,
						currentId[ colInd ],
						id_folder.get( currentId[ colInd ] ).getFileName() ) );
				currentId[ colInd ] += 1;
			}
			System.out.println();
		}
	}


	private boolean satisfactorySelection(
			UiResponse userChoice, int screenCharacterWidth
	) {
		if ( userChoice.command == Command.UI_CHANGE_COLUMN_COUNT
				|| userChoice.command == Command.UI_CHANGE_SCREEN_CHAR_WIDTH )
		{
			Integer userColumns = userChoice.values.iterator().next();
			if ( userColumns < 1 )
			{
				System.out.print( "Must be a positive integer" );
				return false;
			}
			else if ( userChoice.command == Command.UI_CHANGE_COLUMN_COUNT
					&& userColumns > screenCharacterWidth /20 )
			{
				System.out.print( "That's too many columns to divide by" );
				return false;
			}
		}
		else if ( userChoice.command == Command.HIDE_FOLDERS
				|| userChoice.command == Command.TOMBSTONE_FOLDERS )
		{
			for ( Integer value : userChoice.values )
			{
				if ( value < 1 )
				{
					System.out.print( "Must be a positive integer" );
					return false;
				}
			}
		}
		return true;
	}


	/** null when invalid */
	private UiResponse parsedInput(
			String literalInput, Map<Integer, Path> id_folder
	) {
		if ( literalInput == null || literalInput.isEmpty() )
		{
			String complaint = "empty input is not valid input";
			System.out.println( complaint );
			if ( verbose )
				log.info( complaint );
			return null;
		}
		else if ( literalInput.equals( Command.QUIT.getFlag() ) )
		{
			return new UiResponse( Command.QUIT, new LinkedList<>() );
		}
		else if ( ! literalInput.contains( " " ) )
		{
			String complaint = "input needs to separate command from values with a space";
			System.out.println( complaint );
			if ( verbose )
				log.info( complaint );
			return null;
		}
		String[] piecesOfInput = literalInput.split( " " );
		if ( piecesOfInput.length == 1 )
		{
			String complaint = "Needs a value, in addition to the command and a space";
			System.out.println( complaint );
			return null;
		}
		final int piecesIndCommandFlag = 0;
		Command typeOfDesire = Command.fromFlag( piecesOfInput[ piecesIndCommandFlag ] );
		if ( typeOfDesire == Command.UNKNOWN )
		{
			String complaint = "Unrecognized command, expecting "+ listOfCommandFlags();
			System.out.println( complaint );
			if ( verbose )
				log.info( complaint );
			return null;
		}
		if ( typeOfDesire == Command.UI_CHANGE_COLUMN_COUNT
				|| typeOfDesire == Command.UI_CHANGE_SCREEN_CHAR_WIDTH )
		{
			Integer userValue;
			try
			{
				userValue = Integer.parseInt( piecesOfInput[ piecesIndCommandFlag +1 ] );
			}
			catch ( NumberFormatException nfe )
			{
				String complaint = "Ui value must be an integer, not "+ piecesOfInput[ piecesIndCommandFlag +1 ];
				System.out.println( complaint );
				if ( verbose )
					log.info( complaint );
				return null;
			}
			Collection<Integer> value = new LinkedList<>();
			value.add( userValue );
			return new UiResponse( typeOfDesire, value );
		}
		else if ( typeOfDesire == Command.HIDE_FOLDERS
				|| typeOfDesire == Command.TOMBSTONE_FOLDERS )
		{
			Collection<Integer> values = new LinkedList<>();
			Integer userValue;
			for ( int ind = piecesIndCommandFlag +1; ind < piecesOfInput.length; ind++ )
			{
				String someText = piecesOfInput[ ind ];
				if ( ! someText.contains( "-" ) )
				{
					try
					{
						userValue = Integer.parseInt( someText );
						values.add( userValue );
					}
					catch ( NumberFormatException nfe )
					{
						String complaint = "Folder ids must be space or hyphen separated"
								+ "(for a range), not "+ someText;
						System.out.println( complaint );
						if ( verbose )
							log.info( complaint );
						return null;
					}
				}
				else
				{
					int hyphenInd = someText.indexOf( '-' );
					if ( hyphenInd == 0 )
					{
						String complaint = "Folder ids must not be negative";
						System.out.println( complaint );
						if ( verbose )
							log.info( complaint );
						return null;
					}
					String firstV = someText.substring( 0, hyphenInd );
					String secondV = someText.substring( hyphenInd +1 );
					try
					{
						int first = Integer.parseInt( firstV );
						int second = Integer.parseInt( secondV );
						if ( first == second )
							values.add( first );
						else
						{
							if ( first > second )
							{
								int temp = first;
								first = second;
								second = temp;
							}
							values.add( first );
							for ( int val = first +1; val < second; val++ )
								values.add( val );
							values.add( second );
						}
					}
					catch ( NumberFormatException nfe )
					{
						String complaint = "Folder ids must be integers not "+ someText;
						System.out.println( complaint );
						if ( verbose )
							log.info( complaint );
						return null;
					}
				}
			}
			return new UiResponse( typeOfDesire, values );
		}
		else
			return null;
	}


	private String listOfCommandFlags(
	) {
		StringBuilder list = new StringBuilder();
		for ( Command something : Command.values() )
			if ( something != Command.UNKNOWN )
				list.append( something.getFlag() ).append( ", " );
		return list.toString();
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


	private Set<Path> immediateChildrenWithJar(
			Path workingRoot
	) throws IOException {
		/*
		alternative needs jre v9
		return allContents.collect(
			Collectors.filtering(
				( Path candidate ) -> {
					File pAsFolderFile = candidate.toFile();
					if ( ! pAsFolderFile.isDirectory )
						return false;
					String[] filesWithinFolder = pAsFolderFile.list();
					for ( String filenameWithin : filesWithinFolder )
						if ( filenameWithin.endsWith( "jar" ) )
							return true;
					return false;
				},
				Collectors.toSet()
			)
		);
		*/
		Stream<Path> allContents = Files.list( workingRoot );
		Set<Path> allFolders = allContents.collect( Collectors.toSet() );
		allContents.close();
		Iterator<Path> foreach = allFolders.iterator();
		while ( foreach.hasNext() )
		{
			Path candidate = foreach.next();
			File pAsFolderFile = candidate.toFile();
			if ( ! pAsFolderFile.isDirectory() )
			{
				foreach.remove();
				continue;
			}
			String[] filesWithinFolder = pAsFolderFile.list();
			boolean foundOneJar = false;
			for ( String filenameWithin : filesWithinFolder )
				if ( filenameWithin.endsWith( "jar" ) )
				{
					foundOneJar = true;
					break;
				}		
			if ( ! foundOneJar )
			{
				foreach.remove();
				allFolders.remove( candidate );
				continue;
			}
		}
		return allFolders;
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
					if ( verbose )
						log.info( "entering "+ path.toString() );
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
			allContents.close();
		}
		catch ( IOException ie )
		{
			ie.printStackTrace();
			return;
		}
	}


	public void setVerbose(
			boolean beThatWay
	) {
		verbose = beThatWay;
	}


	/** because it carves tombstones */
	private class Sculptor
			implements Consumer<Path>
	{

		public void accept(
				Path target
		) {
			if ( verbose )
				log.info( "handed "+ target.toString() );
			if ( ! target.getFileName().toString().endsWith( "jar" ) )
				return;
			if ( verbose )
				log.info( "using it" );
			try {
				FileTime creationTime = (FileTime)Files.getAttribute( target, "basic:creationTime" );
				FileTime modifiedTime = (FileTime)Files.getAttribute( target, "basic:lastModifiedTime" );
				// improve consider comparing creation with modified, in case I copied this in
				LocalDate createdD = creationTime.toInstant().atZone( ZoneId.systemDefault() ).toLocalDate();
				LocalDate modifiedD = modifiedTime.toInstant().atZone( ZoneId.systemDefault() ).toLocalDate();
				LocalDate earlierD = createdD.compareTo( modifiedD ) < 1 ? createdD : modifiedD;
				String tombstoneName = earlierD.toString() +"_"+ target.getFileName().toString()
						.replace( "jar", "txt" );
				if ( verbose )
					log.info( "creating "+ tombstoneName );
				Path intendedTombstone = target.getParent().resolve( os.getPath( tombstoneName ) );
				if ( verbose )
					log.info( "position "+ intendedTombstone.toString() );
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
























