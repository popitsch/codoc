package at.cibiv.codoc.eval;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class StatsEntry {
	
	public enum ENTRY_TYPE { BGRAPH, TDF }

	public static final int[] BUCKETS = new int[]{0,1,2,4,8,16,32,64,128,256,512,1024};
	
	ENTRY_TYPE type;
	
	String name;

	double maxDiff = 0, countPos = 0, diffPos = 0, meanDiff = 0;

	Map<Integer, double[]> diffMap = new HashMap<Integer, double[]>();

	private long compressionTime;

	private long byteSize;
	
	private File file;
	
	private Object decompressor;
	
	Map<Integer, Float[]> buckets = new HashMap<Integer, Float[]>();

	private long byteSizeGzip;

	private File wigFile;

	public StatsEntry(ENTRY_TYPE type, String name) {
		this.type = type;
		this.name = name;
		for (int i = 5; i < 1000; i += 5)
			diffMap.put(i, new double[2]);
	}

	public double getMaxDiff() {
		return maxDiff;
	}

	public double getCountPos() {
		return countPos;
	}

	public double getDiffPos() {
		return diffPos;
	}

	public double getMeanDiff() {
		return meanDiff/countPos;
	}

	public Map<Integer, double[]> getDiffMap() {
		return diffMap;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("-----------" + type + "/" + name + "-----------");
		sb.append("count-pos\t" + getCountPos() + "\n");
		sb.append("maxi-diff\t" + getMaxDiff() + "\n");
		sb.append("mean-diff\t" + getMeanDiff() + "\n");
		sb.append("diff-posi\t" + getDiffPos() + "\n");
		sb.append("comp-time\t" + getCompressionTime() + "\n");
		sb.append("byte-size\t" + getByteSize() + "\n");
		return sb.toString();
	}

	public long getCompressionTime() {
		return compressionTime;
	}

	public long getByteSize() {
		return byteSize;
	}
	

	public void setByteSizeGzip(long length) {
		this.byteSizeGzip=length;
	}

	public long getByteSizeGzip() {
		return byteSizeGzip;
	}


	public void setCompressionTime(long time) {
		this.compressionTime = time;
	}

	public void setByteSize(long length) {
		this.byteSize = length;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public ENTRY_TYPE getType() {
		return type;
	}

	public void setType(ENTRY_TYPE type) {
		this.type = type;
	}

	public Object getDecompressor() {
		return decompressor;
	}

	public void setDecompressor(Object decompressor) {
		this.decompressor = decompressor;
	}

	public void addData(float foundCoverage, float realCoverage) {
		float diff = foundCoverage - realCoverage;
		countPos++;
		maxDiff = Math.max(maxDiff, Math.abs(diff));
		if (diff != 0)
			diffPos++;
		meanDiff=meanDiff + Math.abs(diff);
		
		int bucketId = BUCKETS.length-1;
		for ( int i = 0; i < BUCKETS.length; i++ ) {
			if ( realCoverage <= BUCKETS[i] ) {
				bucketId = i;
				break;
			}
		}
		Float[] data = buckets.get(bucketId);
		if ( data == null ) {
			data = new Float[4];
			data[0]=0f;
			data[1]=0f;
			data[2]=0f;
			data[3]=0f;
		}
		if ( diff < 0 ) {
			// we are underestimating the coverage
			data[0]++;
			data[1]+= Math.abs(diff);
		} else {
			// we are overestimating the coverage
			data[2]++;
			data[3]+= Math.abs(diff);
		}
		buckets.put(bucketId, data);
	}

	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public Float getMeanDiffForBucket( int bucketId) {
		Float[] data = buckets.get(bucketId);
		if ( data == null )
			return null;
		return (data[1]+data[3])/(data[0]+data[2]);
	}

	public Float getUnderDiffForBucket( int bucketId) {
		Float[] data = buckets.get(bucketId);
		if ( data == null )
			return null;
		return (data[1])/(data[0]);
	}

	public Float getOverDiffForBucket( int bucketId) {
		Float[] data = buckets.get(bucketId);
		if ( data == null )
			return null;
		return (data[3])/(data[2]);
	}

	public Float getCountsForBucket( int bucketId) {
		Float[] data = buckets.get(bucketId);
		if ( data == null )
			return null;
		return (data[0]+data[2]);
	}

	public Float getUnderCountsForBucket( int bucketId) {
		Float[] data = buckets.get(bucketId);
		if ( data == null )
			return null;
		return (data[0]);
	}

	public Float getOverCountsForBucket( int bucketId) {
		Float[] data = buckets.get(bucketId);
		if ( data == null )
			return null;
		return (data[2]);
	}

	public void setWigFile(File wigFile) {
		this.wigFile=wigFile;
	}



}
