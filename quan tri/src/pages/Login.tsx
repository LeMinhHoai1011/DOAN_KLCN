import { Link, useNavigate } from "react-router-dom";
import { useState } from "react";
import "./Login.css";
import { getErrorMessage, login } from "../services/authService";

function Login() {
  const navigate = useNavigate();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  const [error, setError] = useState("");

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();

    setError("");

    if (!username || !password) {
      setError("Vui lòng nhập tên đăng nhập và mật khẩu");
      return;
    }

    try {
      await login({ username, password });
      navigate("/");
    } catch (error: unknown) {
      setError(getErrorMessage(error, "Đăng nhập thất bại hoặc không thể kết nối đến Server"));
    }
  };

  return (
    <div className="login-page">
      <div className="login-box">

        <h1>SMART INVOICE</h1>
        <p className="login-title">Đăng nhập hệ thống</p>

        <form onSubmit={handleLogin}>

          <div className="form-group">
            <label>Tên đăng nhập</label>

            <input
              type="text"
              placeholder="Nhập tên đăng nhập"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
          </div>

          <div className="form-group">
            <label>Mật khẩu</label>

            <input
              type="password"
              placeholder="Nhập mật khẩu"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          {error && (
            <p style={{ color: "red", marginBottom: "15px" }}>
              {error}
            </p>
          )}

          <button type="submit">
            ĐĂNG NHẬP
          </button>

        </form>

        <p className="register-text">
          Chưa có tài khoản?{" "}
          <Link to="/register">Đăng ký</Link>
        </p>

      </div>
    </div>
  );
}

export default Login;