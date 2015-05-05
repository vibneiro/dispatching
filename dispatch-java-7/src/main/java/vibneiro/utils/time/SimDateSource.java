package vibneiro.utils.time;

public class SimDateSource implements DateSource {
    private long ct;

    @Override
    public long currentTimeMillis() {
        return ct;
    }

    public void addMillis(long millis) {
        ct += millis;
    }
}
