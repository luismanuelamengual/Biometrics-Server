--
-- PostgreSQL database dump
--

-- Dumped from database version 12.8 (Ubuntu 12.8-0ubuntu0.20.04.1)
-- Dumped by pg_dump version 12.8 (Ubuntu 12.8-0ubuntu0.20.04.1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: liveness; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.liveness (
    id integer NOT NULL,
    date timestamp without time zone NOT NULL,
    version text,
    clientip text,
    faceimage bytea NOT NULL,
    zoomedfaceimage bytea NOT NULL,
    success boolean NOT NULL,
    status integer NOT NULL,
    clientid integer NOT NULL
);


ALTER TABLE public.liveness OWNER TO postgres;

--
-- Name: liveness_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.liveness_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.liveness_id_seq OWNER TO postgres;

--
-- Name: liveness_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.liveness_id_seq OWNED BY public.liveness.id;


--
-- Name: liveness id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.liveness ALTER COLUMN id SET DEFAULT nextval('public.liveness_id_seq'::regclass);


--
-- Data for Name: liveness; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.liveness (id, date, version, clientip, faceimage, zoomedfaceimage, success, status, clientid) FROM stdin;
\.


--
-- Name: liveness_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.liveness_id_seq', 12, true);


--
-- Name: liveness Id Primary Key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.liveness
    ADD CONSTRAINT "Id Primary Key" PRIMARY KEY (id);


--
-- PostgreSQL database dump complete
--

