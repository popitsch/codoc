      _________  ___  ____  _____
     / ___/ __ \/ _ \/ __ \/ ___/
    / /__/ /_/ / // / /_/ / /__  
    \___/\____/____/\____/\___/  
                                
    Welcome to the CODOC project
    ----------------------------

About
------
CODOC is an open file format and API for the lossless and lossy compression of depth-of-coverage (DOC) signals stemming from high-throughput sequencing (HTS) experiments. DOC data is a one-dimensional signal representing the number of reads covering each reference nucleotide in a HTS data set. DOC is highly-valuable information for interpreting (e.g., RNA-seq), filtering (e.g., SNP analysis) or detecting novel features (e.g., structural variants) in sequencing data sets.

CODOC exploits several characteristics of DOC data stemming from HTS data and uses a non-uniform quantization model that preserves DOC signals in low-coverage regions nearly perfectly while allowing more divergence in highly covered regions.

CODOC reduces required file sizes for DOC data up to 3500X when compared to raw representations and about 4-32X when compared to the other methods. The CODOC API supports efficient sequential and random access to compressed data sets.

Some usage scenarios for CODOC:

* extract strand-specific coverage signal (export as WIG file)
* query coverage values at given genomic positions
* extract genomic regions with a given minimum/maximum coverage
* filter single-nucleotide variations in cancer/normal pairs
* pairwise combinations of coverage files (e.g., subtract one signal from another, calculate minimum signal, etc.)
* cross-correlation of coverage signals
* compression of DOC data (e.g, for archiving or transmission). Note that successive compression/decompression of DOC data with CODOC can be used for intentional data quantization.


![CODOC Overview](https://github.com/popitsch/codoc/blob/master/src/doc/html/img/CoverageSignal.png)

Getting CODOC
------------------

CODOC source code and releases can be downloaded from GitHub. CODOC uses maven2 as a build tool. For development we recommend the Eclipse IDE for Java developers and the m2e Maven Integration for Eclipse.

The CODOC jars can be built with bin/build-java.sh &lt;VERSION> (version is, e.g., 0.0.1)



Usage Examples
------------------

Call the CODOC API w/o parameters to get short usage information for the individual commands:

    $ java -jar bin/codoc-0.0.1.jar
    $ java -jar bin/codoc-0.0.1.jar compress
    $ java -jar bin/codoc-0.0.1.jar decompress
    $ java -jar bin/codoc-0.0.1.jar tools

The following command compresses coverage data extracted from a SAM/BAM file using a regular grid of height 5 for quantization. 
Only reads that have the SAM flag for PCR or optical duplicates (1024) unset and reads with a mapping quality > 20 are included.

    $ java -jar bin/codoc-0.0.1.jar compress -bam <BAM> -qmethod GRID -qparam 5 -filter "FLAGS^^1024" -filter "MAPQ>20" -o BAM.comp

The following command compresses coverage data extracted from all minus-strand reads in a BAM file losslessly. Only reads with a mapping quality > 20 are included.

    $ java -jar bin/codoc-0.0.1.jar compress -bam <BAM> -qparam 0 -filter "STRAND=-" -filter "MAPQ>20" -o <BAM>.comp

The following command prints the header of a compressed file showing the metadata as well as the configuration used for
compressing the file.

    $ java -jar bin/codoc-0.0.1.jar decompress head -covFile <BAM>.comp

The following command starts an interactive query session for random accessing the compressed file.

    $ java -jar bin/codoc-0.0.1.jar decompress query -covFile <BAM>.comp

The following command extracts all regions from the compressed file that have a coverage between MIN and MAX and stores them to a 
[BED](http://genome.ucsc.edu/FAQ/FAQformat.html#format1) file. 
The given name/description are used for the BED track.

    $ java -jar bin/codoc-0.0.1.jar decompress tobed -covFile <BAM>.comp -outFile <BED> -min <MIN> -max <MAX> -name <NAME> -description <DESCRIPTION>

The following command queries a file and scales the results by a linear factor.

    $ java -jar codoc-0.0.1.jar decompress query -covFile <BAM>.comp -scale 2.0

The following commands convert a CODOC file to the [Wig](https://genome.ucsc.edu/goldenPath/help/wiggle.html)
and then to the [BigWig](https://genome.ucsc.edu/goldenPath/help/bigWig.html) format.

    $ java -jar codoc-0.0.1.jar decompress towig -covFile <BAM>.comp -o <BAM>.comp.wig
    $ wigToBigWig <BAM>.comp.wig <chrSizes> <BAM>.comp.bw

**NOTE**: if you run into OutOfMemory errors, you can increase the available heap space of the java JVM (e.g., to 4 GByte) using the
following switch:
    $ java -Xmx4g bin/codoc-0.0.1.jar ...


CODOC Filters
------------------

Filters can be used to restrict the coverage extraction from am SAM/BAM file to reads that match the given filter criteria. 
Multiple filters are combined by a logical *AND*. Filters are of the form &lt;FIELD&gt;&lt;OPERATOR&gt;&lt;VALUE&gt;.

Possible fields:

        MAPQ    	the mapping quality
        FLAGS   	the read flags
        STRAND  	the read strand (+/-)
        FOPSTRAND       the first-of-pair read strand (+/-)

Other names will be mapped directly to the optional field name in the SAM file.
Use e.g., *NM* for the 'number of mismatches' field. Reads that do not have a field
set will be included (See the [SAM specification](http://samtools.sourceforge.net/SAMv1.pdf)).

Possible operators:
        &lt;, &lt;=, =, &gt;, &gt;=, ^^ (flag unset), && (flag set)

Example: (do NOT use reads with mapping quality &lt;= 20, or multiple perfect hits)

        -filter 'MAPQ&gt;20' -filter 'H0=1'

Citation
-----------------

If you make use of CODOC, please cite:

Niko Popitsch, CODOC: efficient access, analysis and compression of depth of coverage signals., 
*Bioinformatics*, **2014**, [DOI](doi:10.1093/bioinformatics/btu362)

