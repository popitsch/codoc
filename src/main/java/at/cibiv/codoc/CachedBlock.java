package at.cibiv.codoc;

import java.util.Map;

public class CachedBlock {

	private CodeWordIntervalTree itree;
	private int blockIdx;
	private Map<String, CodeWordInterval> paddingDownstreamIntervals;
	private Map<String, CodeWordInterval> paddingUpstreamIntervals;

	public CachedBlock(int blockIdx, CodeWordIntervalTree itree, Map<String, CodeWordInterval> paddingDownstreamIntervals,
			Map<String, CodeWordInterval> paddingUpstreamIntervals) {
		this.itree = itree;
		this.blockIdx = blockIdx;
		this.paddingDownstreamIntervals = paddingDownstreamIntervals;
		this.paddingUpstreamIntervals = paddingUpstreamIntervals;
	}

	public CodeWordIntervalTree getItree() {
		return itree;
	}

	public void setItree(CodeWordIntervalTree itree) {
		this.itree = itree;
	}

	public int getBlockIdx() {
		return blockIdx;
	}

	public void setBlockIdx(int blockIdx) {
		this.blockIdx = blockIdx;
	}

	public Map<String, CodeWordInterval> getPaddingDownstreamIntervals() {
		return paddingDownstreamIntervals;
	}

	public void setPaddingDownstreamIntervals(Map<String, CodeWordInterval> paddingDownstreamIntervals) {
		this.paddingDownstreamIntervals = paddingDownstreamIntervals;
	}

	public Map<String, CodeWordInterval> getPaddingUpstreamIntervals() {
		return paddingUpstreamIntervals;
	}

	public void setPaddingUpstreamIntervals(Map<String, CodeWordInterval> paddingUpstreamIntervals) {
		this.paddingUpstreamIntervals = paddingUpstreamIntervals;
	}

	@Override
	public String toString() {
		return "[Block " + getBlockIdx() + "]";
	}

}
