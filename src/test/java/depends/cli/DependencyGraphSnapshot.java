package depends.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import depends.format.json.JCellObject;
import depends.format.json.JDepObject;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class DependencyGraphSnapshot {
    private final JDepObject matrix;

    private DependencyGraphSnapshot(JDepObject matrix) {
        this.matrix = matrix;
    }

    static DependencyGraphSnapshot read(File file) throws IOException {
        return new DependencyGraphSnapshot(new ObjectMapper().readValue(file, JDepObject.class));
    }

    int nodeCount() {
        return matrix.getVariables() == null ? 0 : matrix.getVariables().size();
    }

    int edgeCount() {
        return matrix.getCells() == null ? 0 : matrix.getCells().size();
    }

    int typedEdgeCount() {
        return typedEdges().size();
    }

    Set<TypedEdge> typedEdges() {
        Set<TypedEdge> edges = new HashSet<>();
        if (matrix.getCells() == null || matrix.getVariables() == null) {
            return edges;
        }
        for (JCellObject cell : matrix.getCells()) {
            String source = matrix.getVariables().get(cell.getSrc());
            String target = matrix.getVariables().get(cell.getDest());
            for (Map.Entry<String, Float> entry : cell.getValues().entrySet()) {
                edges.add(new TypedEdge(source, target, entry.getKey()));
            }
        }
        return edges;
    }

    boolean containsEdgeEndingWith(String sourceSuffix, String targetSuffix, String type) {
        for (TypedEdge edge : typedEdges()) {
            if (edge.source.endsWith(sourceSuffix) && edge.target.endsWith(targetSuffix) && edge.type.equals(type)) {
                return true;
            }
        }
        return false;
    }

    Set<TypedEdge> missingFrom(DependencyGraphSnapshot baseline) {
        Set<TypedEdge> missing = baseline.typedEdges();
        missing.removeAll(typedEdges());
        return missing;
    }

    static class TypedEdge {
        final String source;
        final String target;
        final String type;

        TypedEdge(String source, String target, String type) {
            this.source = source;
            this.target = target;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TypedEdge)) {
                return false;
            }
            TypedEdge other = (TypedEdge) o;
            return source.equals(other.source) && target.equals(other.target) && type.equals(other.type);
        }

        @Override
        public int hashCode() {
            int result = source.hashCode();
            result = 31 * result + target.hashCode();
            result = 31 * result + type.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return source + " -> " + target + " [" + type + "]";
        }
    }
}
