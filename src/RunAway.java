import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferStrategy;
import java.util.ArrayList;
import java.util.Arrays;

public class RunAway extends JFrame implements Runnable, MouseListener, MouseMotionListener, KeyListener {
    private final int width = 640;
    private final int height = 480;
    private final Thread thread;
    private boolean running;

    // mouse / keyboard
    private int mouseX, mouseY;

    private String gameState = "menu"; // "menu", "shop", "help", "play", "win", "lose"
    private String selectedColor;
    private Color playerColor;    // color used for drawing player & ball
    private float rainbowHue = 0f;            // 0..1 for HSB rainbow cycle

    // player / world coordinates
    private final double WORLD_MIN = -100;
    private final double WORLD_MAX = 100;
    private double playerx = 0, playery = 0;

    // lines background
    private final double speed = 0.03;

    // destinations
    private final double[] destinationx = {-50, 0, 50, -50, 50, -50, 0, 50};
    private final double[] destinationy = {-50, -50, -50, 0, 0, 50, 50, 50};
    private final int[] pointsFound = new int[8]; // 0 = not visited, 1 = visited


    // enemies
    private final ArrayList<Enemy> enemies = new ArrayList<>();

    // times
    private int time = 0, timesec = 0, timemin = 0, timehour = 0;
    private boolean sec0 = false, min0 = false;

    // shop UI layout
    private final Color[] shopColors = {
            new Color(255, 0, 0),   // Red
            new Color(0, 0, 255),   // Blue
            new Color(0, 255, 0),   // Green
            new Color(255, 255, 0), // Yellow
            new Color(255, 0, 255), // Pink
            new Color(0, 255, 255), // Aqua
            new Color(255, 128, 0), // Orange
            null                    // Rainbow (computed from hue)
    };
    private final String[] shopLabels = {"Red","Blue","Green","Yellow","Pink","Aqua","Orange","Rainbow"};
    private final Point[] shopCenters = new Point[8];

    public RunAway() {
        thread = new Thread(this);

        // compute shop centers (4 across, 2 down)
        int[] cx = {width/5, 2*width/5, 3*width/5, 4*width/5};
        int[] cy = {height/4, height/2};
        int idx = 0;
        for (int row=0; row<2; row++)
            for (int col=0; col<4; col++)
                shopCenters[idx++] = new Point(cx[col], cy[row]);

        // default selected color and player color
        selectedColor = "Red";
        playerColor = shopColors[0];

        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);

        setSize(width, height + 28);
        setResizable(false);
        setTitle("Run Away");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);

        start();
    }

    private synchronized void start() {
        running = true;
        thread.start();
    }

    private void update() {
        // rainbow cycle
        float rainbowSpeed = 0.006f;
        rainbowHue += rainbowSpeed;
        if (rainbowHue > 1f) rainbowHue -= 1f;
        if ("Rainbow".equals(selectedColor)) playerColor = Color.getHSBColor(rainbowHue, 1f, 1f);

        // destinations
        for (int i = 0; i < destinationx.length; i++) {
            if (pointsFound[i] == 0) {
                double dx = playerx - destinationx[i];
                double dy = playery - destinationy[i];
                if (Math.sqrt(dx*dx + dy*dy) < 1) {
                    pointsFound[i] = 1;

                    // spawn new enemy anywhere
                    double ex, ey;
                    do {
                        ex = WORLD_MIN + Math.random() * (WORLD_MAX - WORLD_MIN);
                        ey = WORLD_MIN + Math.random() * (WORLD_MAX - WORLD_MIN);
                    } while (isNearDestination(ex, ey));
                    enemies.add(new Enemy(ex, ey, speed));
                }
            }
        }

        // move enemies toward player
        for (Enemy e : enemies) {
            e.update(playerx, playery); // constantly targets player
        }

        for (Enemy e : enemies) {
            // Hit detection
            double dx = e.x - playerx;
            double dy = e.y - playery;
            if (Math.hypot(dx, dy) < 7.0/12) {
                gameState = "lose";
                return;
            }
        }

        // check win
        boolean allVisited = true;
        for (int i : pointsFound)
            if (i == 0) {
                allVisited = false;
                break;
            }
        if (allVisited) gameState = "win";

        // timers
        time++;
        if (time >= 60) { timesec++; time = 0; }
        if (timesec >= 60) { timemin++; timesec = 0; }
        if (timemin >= 60) { timehour++; timemin = 0; }
        sec0 = timesec < 10;
        min0 = timemin < 10;
    }

    private void render() {
        BufferStrategy bs = getBufferStrategy();
        if (bs == null) { createBufferStrategy(3); return; }
        Graphics2D g = (Graphics2D) bs.getDrawGraphics();
        g.translate(0, 28); // match mouse coordinate adjustments

        // clear
        g.setColor(new Color(0, 200, 200)); // base cyan-ish background from JS
        g.fillRect(0, 0, width, height);

        // draw based on gameState
        switch (gameState) {
            case "menu": renderMenu(g); break;
            case "shop": renderShop(g); break;
            case "help": renderHelp(g); break;
            case "play": renderPlay(g); break;
            case "win": renderWin(g); break;
            case "lose": renderLose(g); break;
        }

        // show buffer
        bs.show();
        g.dispose();
    }

    private void renderMenu(Graphics2D g) {
        // big central PLAY button
        int playCx = width/2, playCy = height/3;
        int playRadius = width/6;
        Color base = Color.WHITE;
        boolean hoverPlay = pointInCircle(mouseX, mouseY, playCx, playCy, playRadius);
        drawCircleButton(g, playCx, playCy, playRadius, "PLAY", base, hoverPlay);

        // help and shop smaller circles
        int helpCx = width/4, shopCx = 3*width/4;
        int helpCy = 3*height/4, radiusSmall = width/8;
        boolean hoverHelp = pointInCircle(mouseX, mouseY, helpCx, helpCy, radiusSmall);
        boolean hoverShop = pointInCircle(mouseX, mouseY, shopCx, helpCy, radiusSmall);
        drawCircleButton(g, helpCx, helpCy, radiusSmall, "HELP", base, hoverHelp);
        drawCircleButton(g, shopCx, helpCy, radiusSmall, "SHOP", base, hoverShop);
    }

    private void renderShop(Graphics2D g) {
        // Title
        g.setFont(g.getFont().deriveFont(Font.BOLD, 48f));
        drawCenteredString(g, "SHOP", width/2, height/8);

        // draw color choices
        int radial = width/16;
        for (int i=0;i<shopCenters.length;i++) {
            Point c = shopCenters[i];
            Color colorToDraw = shopColors[i];
            if (i == shopColors.length-1) { // rainbow slot always cycles
                colorToDraw = Color.getHSBColor(rainbowHue, 1f, 1f);
            }
            boolean hovering = pointInCircle(mouseX, mouseY, c.x, c.y, radial);
            Color fillColor = colorToDraw;
            if (hovering) fillColor = darker(fillColor, 0.75f);
            g.setColor(fillColor);
            g.fillOval(c.x - radial, c.y - radial, radial*2, radial*2);

            // selection border
            if (shopLabels[i].equals(selectedColor)) {
                g.setStroke(new BasicStroke(4f));
                g.setColor(Color.WHITE);
                g.drawOval(c.x - radial - 4, c.y - radial - 4, (radial*2)+8, (radial*2)+8);
            } else {
                g.setStroke(new BasicStroke(1f));
                g.setColor(Color.BLACK);
                g.drawOval(c.x - radial, c.y - radial, radial*2, radial*2);
            }

            // label
            g.setFont(g.getFont().deriveFont(Font.PLAIN, 14f));
            g.setColor(Color.WHITE);
            drawCenteredString(g, shopLabels[i], c.x, c.y + radial + 18);
        }

        // BACK button (looks like a rectangular button with rounded corners)
        int bx = width/2 - width/12, by = 13*height/16 - height/12, bw = width/6, bh = height/6;
        boolean hoverBack = pointInRect(mouseX, mouseY, bx, by, bw, bh);
        drawRectButton(g, bx, by, bw, bh, "BACK", hoverBack);
    }

    private void renderHelp(Graphics2D g) {
        g.setFont(g.getFont().deriveFont(Font.BOLD, 48f));
        drawCenteredString(g, "HELP", width/2, height/8);

        // friendlier/helpful text (reformatted)
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 16f));
        g.setColor(Color.WHITE);
        int startY = 110;
        String[] helpLines = {
                "Welcome to Runaway! Here are some tips:",
                "",
                "• Visit all destinations to win.",
                "• Avoid getting caught by enemies.",
                "• Enemies move toward your location constantly.",
                "",
                "Good luck!"
        };
        for (int i = 0; i < helpLines.length; i++) {
            drawCenteredString(g, helpLines[i], width / 2, startY + i * 24);
        }

        // back button
        int bx = width/2 - width/12, by = 13*height/16 - height/12, bw = width/6, bh = height/6;
        boolean hoverBack = pointInRect(mouseX, mouseY, bx, by, bw, bh);
        drawRectButton(g, bx, by, bw, bh, "BACK", hoverBack);
    }

    private void renderPlay(Graphics2D g) {
        // background lines motion
        double dx = mouseX - width / 2.0;
        double dy = mouseY - height / 2.0;
        double len = Math.sqrt(dx * dx + dy * dy);

        if (len > 1) { // avoid jitter at the exact center
            dx /= len;
            dy /= len;

            // Player movement update
            playerx += dx * speed / 10;
            playery += dy * speed / 10;

            // Clamp player within world bounds
            playerx = clamp(playerx, WORLD_MIN, WORLD_MAX);
            playery = clamp(playery, WORLD_MIN, WORLD_MAX);
        }

        int gridSpacing = 1;
        int numLinesX = (int) ((WORLD_MAX - WORLD_MIN) / gridSpacing);
        int numLinesY = (int) ((WORLD_MAX - WORLD_MIN) / gridSpacing);

        g.setColor(new Color(64, 64, 64,80));
        for (int i = -numLinesX; i <= numLinesX; i++) {
            double worldX = i * gridSpacing;
            int screenX = worldToScreenX(worldX); // project to screen
            g.drawLine(screenX, 0, screenX, height);
        }

        for (int i = -numLinesY; i <= numLinesY; i++) {
            double worldY = i * gridSpacing;
            int screenY = worldToScreenY(worldY); // project to screen
            g.drawLine(0, screenY, width, screenY);
        }

        // draw player
        g.setColor(playerColor);
        double x = width / 2.0;
        // Player movement update
        double y = height / 2.0;
        int px = (int)Math.round(x), py = (int)Math.round(y);
        int pradius = width/32;
        g.fillOval(px - pradius, py - pradius, 2 * pradius, 2 * pradius);

        g.setColor(playerColor.darker());
        g.setStroke(new BasicStroke(2));
        g.drawOval(px - pradius, py - pradius, 2 * pradius, 2 * pradius);

        // draw enemies on main screen
        int eradius = width/24;
        for (Enemy e : enemies) {
            int exScreen = worldToScreenX(e.x);
            int eyScreen = worldToScreenY(e.y);
            g.setColor(Color.BLACK);
            g.fillOval(exScreen - eradius, eyScreen - eradius, 2 * eradius, 2 * eradius);
        }

        // draw destinations in the main world
        for (int i = 0; i < destinationx.length; i++) {
            // project from world coords to screen
            double sx = (playerx - destinationx[i]) * -width/8.0 + width/2.0;
            double sy = (playery - destinationy[i]) * -height/8.0 + height/2.0;

            int radius = width / 4;
            if (pointsFound[i] == 1) {
                g.setColor(new Color(0, 255, 0, 80)); //  green
            } else {
                g.setColor(new Color(255, 0, 0, 80)); //  red
            }
            g.fillOval((int)(sx - radius / 2.0), (int)(sy - radius / 2.0), radius, radius);

            // outline
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(2f));
            g.drawOval((int)(sx - radius / 2.0), (int)(sy - radius / 2.0), radius, radius);
        }

        // simple minimap (top-right)
        int mx = 39*width/48, my = width/48, mw = width/6, mh = width/6;
        g.setColor(new Color(255,255,255,120));
        g.fillRect(mx, my, mw, mh);
        g.setColor(new Color(255,255,255));
        g.drawRect(mx, my, mw, mh);

        // draw player on minimap
        double minimapx = projectToMinimapX(playerx, mx, mw);
        double minimapy = projectToMinimapY(playery, my, mh);
        g.setColor(playerColor);
        g.fillOval((int)minimapx - 3, (int)minimapy - 3, 6, 6);

        // draw destinations on minimap
        for (int i = 0; i < destinationx.length; i++) {
            double mxPos = projectToMinimapX(destinationx[i], mx, mw);
            double myPos = projectToMinimapY(destinationy[i], my, mh);
            g.setColor(pointsFound[i] == 1 ? new Color(0,255,0,80) : new Color(255,0,0,80));
            g.fillOval((int)mxPos - 4, (int)myPos - 4, 8, 8);
            g.setColor(Color.BLACK);
            g.drawOval((int)mxPos - 4, (int)myPos - 4, 8, 8);
        }

        // draw enemies on minimap
        for (Enemy e : enemies) {
            double ex = projectToMinimapX(e.x, mx, mw);
            double ey = projectToMinimapY(e.y, my, mh);
            g.setColor(Color.BLACK);
            g.fillOval((int)ex - 3, (int)ey - 3, 6, 6);
        }

        // draw HUD (destinations left and time)
        g.setColor(new Color(255,255,255,150));
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 15f));
        drawLeftString(g, "DESTINATIONS LEFT : " + (8 - pointsFoundCount()), 10, 20);

        // time
        String timeText;
        if (sec0 && min0) timeText = String.format("Time: %d : 0%d : 0%d", timehour, timemin, timesec);
        else if (min0) timeText = String.format("Time: %d : 0%d : %d", timehour, timemin, timesec);
        else if (sec0) timeText = String.format("Time: %d : %d : 0%d", timehour, timemin, timesec);
        else timeText = String.format("Time: %d : %d : %d", timehour, timemin, timesec);
        drawLeftString(g, timeText, 10, 50);
    }

    private void renderWin(Graphics2D g) {
        g.setColor(new Color(0,180,180));
        g.fillRect(0,0,width,height);
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 60f));
        drawCenteredString(g, "YOU WIN!!!", width/2, height/4);
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 20f));
        drawCenteredString(g, String.format("Time: %d : %02d : %02d", timehour, timemin, timesec), width/2, height/2);

        // PLAY AGAIN button (smaller so text fits)
        int bw = width/5, bh = height/10;
        int bx = width/2 - bw/2, by = 3*height/4 - bh/2;
        boolean hover = pointInRect(mouseX, mouseY, bx, by, bw, bh);
        drawRectButton(g, bx, by, bw, bh, "PLAY AGAIN", hover);
    }

    private void renderLose(Graphics2D g) {
        g.setColor(Color.RED);
        g.fillRect(0,0,width,height);
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 50f));
        drawCenteredString(g, "You got hit!", width/2, height/4);
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 20f));
        drawCenteredString(g, String.format("Time: %d : %02d : %02d", timehour, timemin, timesec), width/2, height/2);

        int bw = width/5, bh = height/10;
        int bx = width/2 - bw/2, by = 3*height/4 - bh/2;
        boolean hover = pointInRect(mouseX, mouseY, bx, by, bw, bh);
        drawRectButton(g, bx, by, bw, bh, "TRY AGAIN", hover);
    }

    private int projectToMinimapX(double wx, double mx, double mw) {
        return (int)((wx - WORLD_MIN) / (WORLD_MAX - WORLD_MIN) * mw + mx);
    }
    private int projectToMinimapY(double wy, double my, double mh) {
        return (int)((wy - WORLD_MIN) / (WORLD_MAX - WORLD_MIN) * mh + my);
    }

    // Screen projection helper
    private int worldToScreenX(double wx) {
        return (int) ((playerx - wx) * -width / 8.0 + width / 2.0);
    }

    private int worldToScreenY(double wy) {
        return (int) ((playery - wy) * -height / 8.0 + height / 2.0);
    }

    private void drawCircleButton(Graphics2D g, int cx, int cy, int radius, String label, Color baseColor, boolean hover) {
        Color fill = hover ? darker(baseColor, 0.8f) : baseColor;
        g.setColor(fill);
        g.fillOval(cx - radius, cy - radius, radius*2, radius*2);
        // border
        g.setStroke(new BasicStroke(3f));
        g.setColor(Color.BLACK);
        g.drawOval(cx - radius, cy - radius, radius*2, radius*2);
        // label
        g.setColor(Color.DARK_GRAY);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 40f));
        drawCenteredString(g, label, cx, cy);
    }

    private void drawRectButton(Graphics2D g, int x, int y, int w, int h, String label, boolean hover) {
        Color base = new Color(255,255,255);
        Color fill = hover ? darker(base, 0.8f) : base;
        g.setColor(fill);
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x, y, w, h, 10, 10);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 20f));
        g.setColor(new Color(0,180,180));
        drawCenteredString(g, label, x + w/2, y + h/2);
    }

    private Color darker(Color c, float factor) {
        int r = (int)Math.max(0, c.getRed() * factor);
        int g = (int)Math.max(0, c.getGreen() * factor);
        int b = (int)Math.max(0, c.getBlue() * factor);
        return new Color(r,g,b, c.getAlpha());
    }

    private void drawCenteredString(Graphics2D g, String text, int cx, int cy) {
        FontMetrics fm = g.getFontMetrics();
        int x = cx - fm.stringWidth(text)/2;
        int y = cy - fm.getHeight()/2 + fm.getAscent();
        g.drawString(text, x, y);
    }

    private void drawLeftString(Graphics2D g, String text, int x, int y) {
        g.drawString(text, x, y);
    }

    private boolean pointInCircle(int px, int py, int cx, int cy, int r) {
        double dx = px - cx;
        double dy = py - cy;
        return dx*dx + dy*dy <= r*r;
    }

    private boolean pointInRect(int px, int py, int rx, int ry, int rw, int rh) {
        return (px >= rx && px <= rx+rw && py >= ry && py <= ry+rh);
    }

    private double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }

    private int pointsFoundCount() {
        int sum = 0;
        for (int j : pointsFound) sum += j;
        return sum;
    }

    // We process clicks on mouseReleased to avoid accidental "press-through".
    public void mouseClicked(MouseEvent me) { /* Not used; mouseReleased handles clicks */ }
    public void mouseEntered(MouseEvent me) {}
    public void mouseExited(MouseEvent me) {}
    public void mousePressed(MouseEvent me) {
        // keep coords accurate
        mouseX = me.getX() - 1;
        mouseY = me.getY() - 31;
    }
    public void mouseReleased(MouseEvent me) {
        mouseX = me.getX() - 1;
        mouseY = me.getY() - 31;
        handleClick(mouseX, mouseY);
    }
    public void mouseDragged(MouseEvent me) {
        mouseX = me.getX() - 1;
        mouseY = me.getY() - 31;
    }
    public void mouseMoved(MouseEvent me) {
        mouseX = me.getX() - 1;
        mouseY = me.getY() - 31;
    }

    // click handling separated for clarity / consistent hitboxes
    private void handleClick(int mx, int my) {
        if ("menu".equals(gameState)) {
            // Play circle
            if (pointInCircle(mx,my, width/2, height/3, width/6)) {
                gameState = "play";
                playerx = 0; playery = 0;
                Arrays.fill(pointsFound, 0);

                // spawn initial 3 enemies anywhere (not near destinations)
                enemies.clear();
                for (int i=0;i<3;i++) {
                    double ex, ey;
                    do {
                        ex = WORLD_MIN + Math.random() * (WORLD_MAX - WORLD_MIN);
                        ey = WORLD_MIN + Math.random() * (WORLD_MAX - WORLD_MIN);
                    } while (isNearDestination(ex, ey));
                    enemies.add(new Enemy(ex, ey, speed));
                }
                return;
            }

            // Help circle
            if (pointInCircle(mx,my, width/4, 3*height/4, width/8)) {
                gameState = "help";
                return;
            }
            // Shop circle
            if (pointInCircle(mx,my, 3*width/4, 3*height/4, width/8)) {
                gameState = "shop";
            }
        } else if ("shop".equals(gameState)) {
            // check each shop color
            int radial = width/16;
            for (int i=0;i<shopCenters.length;i++) {
                Point c = shopCenters[i];
                if (pointInCircle(mx,my, c.x, c.y, radial)) {
                    selectedColor = shopLabels[i];
                    // set playerColor immediately (rainbow will update in update())
                    if (!"Rainbow".equals(selectedColor)) {
                        playerColor = shopColors[i];
                    } else {
                        // set to current rainbow hue
                        playerColor = Color.getHSBColor(rainbowHue, 1f, 1f);
                    }
                    return;
                }
            }
            // back button
            int bx = width/2 - width/12, by = 13*height/16 - height/12, bw = width/6, bh = height/6;
            if (pointInRect(mx,my,bx,by,bw,bh)) {
                gameState = "menu";
            }
        } else if ("help".equals(gameState)) {
            int bx = width/2 - width/12, by = 13*height/16 - height/12, bw = width/6, bh = height/6;
            if (pointInRect(mx,my,bx,by,bw,bh)) {
                gameState = "menu";
            }
        } else if ("win".equals(gameState)) {
            int bw = width/5, bh = height/10;
            int bx = width/2 - bw/2, by = 3*height/4 - bh/2;
            if (pointInRect(mx, my, bx, by, bw, bh)) {
                startPlayAgain();
            }
        } else if ("lose".equals(gameState)) {
            int bw = width/5, bh = height/10;
            int bx = width/2 - bw/2, by = 3*height/4 - bh/2;
            if (pointInRect(mx, my, bx, by, bw, bh)) {
                startPlayAgain();
            }
        }
    }
    private void startPlayAgain() {
        Arrays.fill(pointsFound, 0);
        playerx = 0; playery = 0;

        // spawn 3 initial enemies
        enemies.clear();
        for (int i = 0; i < 3; i++) {
            double ex, ey;
            do {
                ex = WORLD_MIN + Math.random() * (WORLD_MAX - WORLD_MIN);
                ey = WORLD_MIN + Math.random() * (WORLD_MAX - WORLD_MIN);
            } while (isNearDestination(ex, ey));
            enemies.add(new Enemy(ex, ey, speed));
        }

        gameState = "play";
    }

    private boolean isNearDestination(double ex, double ey) {
        for (int i = 0; i < destinationx.length; i++) {
            if (pointsFound[i] == 0) {
                double dx = ex - destinationx[i];
                double dy = ey - destinationy[i];
                if (Math.hypot(dx, dy) < 5) return true; // avoid spawning too close
            }
        }
        return false;
    }

    public void run()
    {
        //main program loop
        long lastTime = System.nanoTime();
        final double ns = 1000000000.0 / 60.0; //60 times per second
        double delta = 0;
        requestFocus();
        while(running)
        {
            //updates time
            long now = System.nanoTime();
            delta += (now - lastTime) / ns;
            lastTime = now;
            while(delta >= 1) //Make sure update is only happening 60 times a second
            {
                //update
                update();
                delta--;
            }
            //display to the screen
            render();
        }
    }

    // keyboard (not used heavily in current port, but required by interface)
    public void keyPressed(KeyEvent key) {}
    public void keyReleased(KeyEvent key) {}
    public void keyTyped(KeyEvent key) {}

    public static void main(String[] args) { SwingUtilities.invokeLater(RunAway::new); }

    class Enemy {
        double x, y;
        double speed;

        Enemy(double x, double y, double speed) {
            this.x = x;
            this.y = y;
            this.speed = speed;
        }

        void update(double px, double py) {
            double dx = px - x;
            double dy = py - y;
            double len = Math.hypot(dx, dy);
            if (len > 0) {
                x += dx / len * speed; // constant movement toward player
                y += dy / len * speed;
                x = clamp(x, WORLD_MIN, WORLD_MAX);
                y = clamp(y, WORLD_MIN, WORLD_MAX);
            }
        }
    }
}
