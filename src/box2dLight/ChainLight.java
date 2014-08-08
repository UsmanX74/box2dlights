package box2dLight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Mesh.VertexDataType;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Pools;

public class ChainLight extends Light {
  private Body body;
  private float bodyOffsetX;
  private float bodyOffsetY;

  float[] vertices;
  float[] lengths;
  float[] angles;
  final float[] startX;
  final float[] startY;
  final float[] perpX;
  final float[] perpY;
  final float endX[];
  final float endY[];

  /**
   * attach positional light to automatically follow body. Position is fixed to
   * given offset.
   */
  @Override
  public void attachToBody(Body body, float offsetX, float offSetY) {
    this.body = body;
    bodyOffsetX = offsetX;
    bodyOffsetY = offSetY;
    if (staticLight)
      staticUpdate();
  }

  @Override
  public Vector2 getPosition() {
    return tmpPosition;
  }

  public Body getBody() {
    return body;
  }

  /** horizontal starting position of light in world coordinates. */
  @Override
  public float getX() {
    return tmpPosition.x;
  }

  /** vertical starting position of light in world coordinates. */
  @Override
  public float getY() {
    return tmpPosition.y;
  }

  private final Vector2 tmpEnd = new Vector2();
  private final Vector2 tmpStart = new Vector2();
  private final Vector2 tmpPerp = new Vector2();

  @Override
  public void setPosition(float x, float y) {
    tmpPosition.x = x;
    tmpPosition.y = y;
    if (staticLight)
      staticUpdate();
  }

  @Override
  public void setPosition(Vector2 position) {
    tmpPosition.x = position.x;
    tmpPosition.y = position.y;
    if (staticLight)
      staticUpdate();
  }

  @Override
  void update() {
    if (body != null && !staticLight) {
      final Vector2 vec = body.getPosition();
      float angle = body.getAngle();
      final float cos = MathUtils.cos(angle);
      final float sin = MathUtils.sin(angle);
      final float dX = bodyOffsetX * cos - bodyOffsetY * sin;
      final float dY = bodyOffsetX * sin + bodyOffsetY * cos;
      tmpPosition.x = vec.x + dX;
      tmpPosition.y = vec.y + dY;

    }

    if (rayHandler.culling) {
      culled = ((!rayHandler.intersect(tmpPosition.x, tmpPosition.y, distance
          + softShadowLength)));
      if (culled)
        return;
    }

    if (staticLight)
      return;

    initRays();

    for (int i = 0; i < rayNum; i++) {
      m_index = i;
      f[i] = 1f;
      tmpEnd.x = endX[i];// + tmpPosition.x;
      mx[i] = tmpEnd.x;
      tmpEnd.y = endY[i];// + tmpPosition.y;
      my[i] = tmpEnd.y;
      tmpStart.x = startX[i];
      tmpStart.y = startY[i];
      if (rayHandler.world != null && !xray) {
        rayHandler.world.rayCast(ray, tmpStart, tmpEnd);
      }
    }
    setMesh();
  }

  void setMesh() {

    // ray starting point
    int size = 0;

    // rays ending points.
    for (int i = 0; i < rayNum; i++) {
      segments[size++] = startX[i];
      segments[size++] = startY[i];
      segments[size++] = colorF;
      segments[size++] = 1;
      segments[size++] = mx[i];
      segments[size++] = my[i];
      segments[size++] = colorF;
      segments[size++] = 1 - f[i];
    }
    lightMesh.setVertices(segments, 0, size);

    if (!soft || xray)
      return;

    size = 0;
    // rays ending points.

    for (int i = 0; i < rayNum; i++) {

      segments[size++] = mx[i];
      segments[size++] = my[i];
      segments[size++] = colorF;
      final float s = (1 - f[i]);
      segments[size++] = s;

      tmpPerp.set(perpX[i], perpY[i]).scl(softShadowLength * s)
          .add(mx[i], my[i]);
      segments[size++] = tmpPerp.x;
      segments[size++] = tmpPerp.y;
      segments[size++] = zero;
      segments[size++] = 0f;
    }
    softShadowMesh.setVertices(segments, 0, size);
  }

  @Override
  void render() {
    if (rayHandler.culling && culled)
      return;

    rayHandler.lightRenderedLastFrame++;
    lightMesh.render(rayHandler.lightShader, GL20.GL_TRIANGLE_STRIP, 0,
        vertexNum);
    if (soft && !xray) {
      softShadowMesh.render(rayHandler.lightShader, GL20.GL_TRIANGLE_STRIP, 0,
          (vertexNum - 1) * 2);
    }
  }

  public ChainLight(RayHandler rayHandler, int rays, Color color,
      float distance, float x, float y, float[] vertices) {
    super(rayHandler, rays, color, 0f, distance);

    vertexNum = (vertexNum - 1) * 2;

    tmpPosition.x = x;
    tmpPosition.y = y;
    endX = new float[rays];
    endY = new float[rays];
    startX = new float[rays];
    startY = new float[rays];
    perpX = new float[rays];
    perpY = new float[rays];
    lengths = new float[vertices.length / 4];
    angles = new float[vertices.length / 4];
    this.vertices = vertices;

    initRays();

    lightMesh = new Mesh(VertexDataType.VertexArray, false, vertexNum, 0,
        new VertexAttribute(Usage.Position, 2, "vertex_positions"),
        new VertexAttribute(Usage.ColorPacked, 4, "quad_colors"),
        new VertexAttribute(Usage.Generic, 1, "s"));
    softShadowMesh = new Mesh(VertexDataType.VertexArray, false, vertexNum * 2,
        0, new VertexAttribute(Usage.Position, 2, "vertex_positions"),
        new VertexAttribute(Usage.ColorPacked, 4, "quad_colors"),
        new VertexAttribute(Usage.Generic, 1, "s"));
    setMesh();
  }

  public void initRays() {
    Vector2 v = Pools.obtain(Vector2.class);
    Vector2 v1 = Pools.obtain(Vector2.class);
    Vector2 v2 = Pools.obtain(Vector2.class);
    Vector2 direction = Pools.obtain(Vector2.class);
    Vector2 perp = Pools.obtain(Vector2.class);

    int segment = 0;
    int lastSegment = vertices.length / 2;
    float chainLength = 0;
    for (int i = 0; i < lastSegment; i += 2) {
      v.set(vertices[i + 2], vertices[i + 3]).sub(vertices[i], vertices[i + 1]);
      lengths[segment] = v.len();
      chainLength += lengths[segment];
      segment++;
    }

    float raySpacing = chainLength / rayNum;

    // |--------|------|-----------| segments
    // |---|---|---|---|---|---|---| raySpacing
    // |---| step

    segment = 0;
    int updatingSegment = -1;
    float step = 0;
    for (int i = 0; i < rayNum; i++) {
      while (step > lengths[segment]) {
        step -= lengths[segment];
        segment++;
      }
      if (updatingSegment != segment) {
        updatingSegment = segment;
        int vertex = segment * 4;
        v1.set(vertices[vertex], vertices[vertex + 1]);
        v2.set(vertices[vertex + 2], vertices[vertex + 3]);
        direction.set(v2).sub(v1).nor();
        perp.set(direction).rotate90(1).scl(distance);
      }

      v.set(direction).scl(step).add(v1);
      this.startX[i] = v.x;
      this.startY[i] = v.y;
      v.add(perp);
      this.endX[i] = v.x;
      this.endY[i] = v.y;

      step += raySpacing;
    }

    Pools.free(v);
    Pools.free(v1);
    Pools.free(v2);
    Pools.free(direction);
    Pools.free(perp);
  }

  @Override
  public boolean contains(float x, float y) {

    // fast fail
    final float x_d = tmpPosition.x - x;
    final float y_d = tmpPosition.y - y;
    final float dst2 = x_d * x_d + y_d * y_d;
    if (distance * distance <= dst2)
      return false;

    // actual check

    boolean oddNodes = false;
    float x2 = mx[rayNum] = tmpPosition.x;
    float y2 = my[rayNum] = tmpPosition.y;
    float x1, y1;
    for (int i = 0; i <= rayNum; x2 = x1, y2 = y1, ++i) {
      x1 = mx[i];
      y1 = my[i];
      if (((y1 < y) && (y2 >= y)) || (y1 >= y) && (y2 < y)) {
        if ((y - y1) / (y2 - y1) * (x2 - x1) < (x - x1))
          oddNodes = !oddNodes;
      }
    }
    return oddNodes;

  }

  @Override
  public void setDirection(float directionDegree) {
  }

}
